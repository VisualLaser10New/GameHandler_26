package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.testsupport.ListAppender;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * R2 — local ordering guard against password-hash regression under retry
 * reordering. Verifies the {@link UserSyncService#syncUsers} per-dto event-time
 * guard semantics:
 * <ul>
 *   <li><b>stale</b> (existing eventTime &gt; incoming occurredAt) → skip + WARN.</li>
 *   <li><b>newer</b> (incoming occurredAt &gt; existing eventTime) → apply (save).</li>
 *   <li><b>first-ever</b> (no existing row) → apply with eventTime = occurredAt.</li>
 *   <li><b>mixed batch</b> → exactly the non-stale dtos are saved.</li>
 *   <li><b>null occurredAt</b> (legacy 4-field JSON) → apply (do NOT skip).</li>
 * </ul>
 *
 * <p>The guard is the R2 defence against password-hash regression: when a retry
 * storm delivers an older snapshot AFTER a newer one has already been applied,
 * the older event's {@code occurredAt} is strictly before the persisted row's
 * {@code event_time}, so it is dropped instead of overwriting the (correct,
 * newer) password hash with a stale one.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserSyncServiceOrderingGuardTest {

    private static final Instant T0 = Instant.parse("2026-07-06T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-06T11:00:00Z");
    private static final Instant FIXED_NOW = Instant.parse("2026-07-06T12:00:00Z");

    @Mock UserRepository userRepository;
    @Mock AdminRequestRepository adminRequestRepository;

    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    private UserSyncService service;
    private ListAppender appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new UserSyncService(userRepository, adminRequestRepository, clock);
        logger = (Logger) LoggerFactory.getLogger(UserSyncService.class);
        appender = new ListAppender();
        logger.addAppender(appender);
        appender.start();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private User existing(String id, Instant eventTime) {
        return new User(new UserId(id), "alice", "hash-old", "a@example.com", List.of("PLAYER"), eventTime, eventTime);
    }

    private UserSyncDto dto(String id, Instant occurredAt) {
        return new UserSyncDto(id, "alice", "a@example.com", "hash-new", List.of("PLAYER"), occurredAt);
    }

    @Test
    void staleEvent_isSkipped_notSaved_andWarns() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.of(existing("u-1", T1)));

        service.syncUsers(List.of(dto("u-1", T0)));

        verify(userRepository, never()).save(any());
        boolean warned = appender.getEvents().stream().anyMatch(e ->
                e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("u-1")
                        && e.getFormattedMessage().contains("skipping"));
        assertThat(warned).as("stale event must emit a WARN with the userId and 'skipping'").isTrue();
    }

    @Test
    void newerEvent_isApplied_withIncomingOccurredAtAsEventTime() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.of(existing("u-1", T0)));

        service.syncUsers(List.of(dto("u-1", T1)));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEventTime()).isEqualTo(T1);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void firstEverEvent_isApplied_withOccurredAtAsEventTime() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.empty());

        service.syncUsers(List.of(dto("u-1", T0)));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEventTime()).isEqualTo(T0);
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void mixedBatch_onlyNonStaleDtosAreSaved() {
        when(userRepository.findById(new UserId("u-stale"))).thenReturn(Optional.of(existing("u-stale", T1)));
        when(userRepository.findById(new UserId("u-newer"))).thenReturn(Optional.of(existing("u-newer", T0)));
        when(userRepository.findById(new UserId("u-first"))).thenReturn(Optional.empty());

        service.syncUsers(List.of(
                dto("u-stale", T0),
                dto("u-newer", T1),
                dto("u-first", T0)
        ));

        verify(userRepository, times(2)).save(any());
        verify(userRepository, never()).save(argThat(u -> "u-stale".equals(u.getUserId().value())));
        verify(userRepository, times(1)).save(argThat(u -> "u-newer".equals(u.getUserId().value())));
        verify(userRepository, times(1)).save(argThat(u -> "u-first".equals(u.getUserId().value())));
    }

    @Test
    void nullOccurredAt_isApplied_neverSkipped() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.of(existing("u-1", T1)));

        UserSyncDto legacy = new UserSyncDto("u-1", "alice", "hash-new", List.of("PLAYER"));

        service.syncUsers(List.of(legacy));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEventTime()).isEqualTo(FIXED_NOW);
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }
}
