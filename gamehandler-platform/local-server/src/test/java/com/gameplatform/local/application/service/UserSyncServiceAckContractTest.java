package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * M3 — per-user ACK contract for {@link UserSyncService#syncUsers}.
 *
 * <p>Covers the four ack flavours and the input-order contract:
 * <ul>
 *   <li>happy → {@code (id, true, null)} + save.</li>
 *   <li>stale → {@code (id, true, "STALE_EVENT")} + NO save.</li>
 *   <li>poison (blank username) → {@code (id, false, "VALIDATION_ERROR:...")} + NO save + batch continues.</li>
 *   <li>mixed batch of 3 → exactly 3 acks returned, in input order.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserSyncServiceAckContractTest {

    private static final Instant T0 = Instant.parse("2026-07-06T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-06T11:00:00Z");
    private static final Instant FIXED_NOW = Instant.parse("2026-07-06T12:00:00Z");

    @Mock UserRepository userRepository;

    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    private UserSyncService service;

    @BeforeEach
    void setUp() {
        service = new UserSyncService(userRepository, clock);
    }

    private User existing(String id, Instant eventTime) {
        return new User(new UserId(id), "alice", "hash-old", "a@example.com", List.of("PLAYER"), eventTime, eventTime);
    }

    private UserSyncDto dto(String id, Instant occurredAt) {
        return new UserSyncDto(id, "alice", "a@example.com", "hash-new", List.of("PLAYER"), occurredAt);
    }

    @Test
    void happyPath_returnsAppliedTrueAckAndSaves() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.empty());

        List<UserSyncAckDto> acks = service.syncUsers(List.of(dto("u-1", T0)));

        assertThat(acks).hasSize(1);
        assertThat(acks.get(0)).isEqualTo(new UserSyncAckDto("u-1", true, null));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void staleEvent_returnsStaleEventAckAndDoesNotSave() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.of(existing("u-1", T1)));

        List<UserSyncAckDto> acks = service.syncUsers(List.of(dto("u-1", T0)));

        assertThat(acks).hasSize(1);
        assertThat(acks.get(0).userId()).isEqualTo("u-1");
        assertThat(acks.get(0).applied()).isTrue();
        assertThat(acks.get(0).reason()).isEqualTo("STALE_EVENT");
        verify(userRepository, never()).save(any());
    }

    @Test
    void poisonUser_returnsValidationErrorAckAndDoesNotSave() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.empty());
        UserSyncDto poison = new UserSyncDto("u-1", "", "hash-new", List.of("PLAYER"));

        List<UserSyncAckDto> acks = service.syncUsers(List.of(poison));

        assertThat(acks).hasSize(1);
        assertThat(acks.get(0).userId()).isEqualTo("u-1");
        assertThat(acks.get(0).applied()).isFalse();
        assertThat(acks.get(0).reason()).startsWith("VALIDATION_ERROR");
        verify(userRepository, never()).save(any());
    }

    @Test
    void mixedBatch_returnsOneAckPerInputInOrder() {
        when(userRepository.findById(new UserId("u-stale"))).thenReturn(Optional.of(existing("u-stale", T1)));
        when(userRepository.findById(new UserId("u-poison"))).thenReturn(Optional.empty());
        when(userRepository.findById(new UserId("u-happy"))).thenReturn(Optional.empty());

        UserSyncDto poison = new UserSyncDto("u-poison", "", "hash", List.of("PLAYER"));
        List<UserSyncAckDto> acks = service.syncUsers(List.of(
                dto("u-stale", T0),   // stale
                poison,               // poison (blank username)
                dto("u-happy", T0)    // happy
        ));

        assertThat(acks).hasSize(3);
        assertThat(acks.get(0).userId()).isEqualTo("u-stale");
        assertThat(acks.get(0).applied()).isTrue();
        assertThat(acks.get(0).reason()).isEqualTo("STALE_EVENT");
        assertThat(acks.get(1).userId()).isEqualTo("u-poison");
        assertThat(acks.get(1).applied()).isFalse();
        assertThat(acks.get(1).reason()).startsWith("VALIDATION_ERROR");
        assertThat(acks.get(2).userId()).isEqualTo("u-happy");
        assertThat(acks.get(2).applied()).isTrue();
        assertThat(acks.get(2).reason()).isNull();
        // Only the happy user is saved; stale and poison are not.
        verify(userRepository, times(1)).save(argThat(u -> "u-happy".equals(u.getUserId().value())));
        verify(userRepository, never()).save(argThat(u -> "u-stale".equals(u.getUserId().value())));
        verify(userRepository, never()).save(argThat(u -> "u-poison".equals(u.getUserId().value())));
    }
}
