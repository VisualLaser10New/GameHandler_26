package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushGameDefinitionToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R1 + M3 unit coverage for {@link LateRegistrationCatchUpService}.
 *
 * <p>Pure Mockito (no Spring context). Asserts:
 * <ul>
 *   <li>SENT <em>and</em> PENDING events are replayed (REPLAY_STATUSES).</li>
 *   <li>{@code replication_progress} is saved per-event via the domain port.</li>
 *   <li>Duplicate progress insert (DIVE) is swallowed — catch-up continues, no rethrow.</li>
 *   <li>Empty SENT/PENDING set is a no-op.</li>
 *   <li>Poison user ({@code applied=false}, {@code reason=VALIDATION_ERROR:...}) → that event
 *       is {@code markAsFailed}ed and NO progress is saved, while sibling events proceed.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LateRegistrationCatchUpReplaysPendingAndRecordsProgressTest {

    @Mock private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PushUserToLocalServersPort pushUserToLocalServersPort;
    @Mock private PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    @Mock private PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    @Mock private ReplicationProgressRepository replicationProgressRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LateRegistrationCatchUpService service;

    @BeforeEach
    void setUp() {
        service = new LateRegistrationCatchUpService(
                outboxEventJpaRepository,
                outboxEventRepository,
                pushUserToLocalServersPort,
                objectMapper,
                replicationProgressRepository,
                pushMetadataToLocalServersPort,
                pushGameDefinitionToLocalServersPort);
    }

    private OutboxEventJpaEntity event(String id, String status, UserSyncDto dto) throws Exception {
        return new OutboxEventJpaEntity(
                id,
                "USER_REGISTERED",
                objectMapper.writeValueAsString(dto),
                status,
                Instant.parse("2026-07-05T00:00:00Z"),
                "SENT".equals(status) ? Instant.parse("2026-07-05T00:00:05Z") : null);
    }

    private RegisteredLocalServer newServer(String buildingId) {
        return new RegisteredLocalServer(
                new BuildingId(buildingId),
                "https://" + buildingId + ":8081",
                Instant.parse("2026-07-05T12:00:00Z"),
                true);
    }

    @Test
    void replaysBothSentAndPendingEventsAndRecordsProgressPerEvent() throws Exception {
        UserSyncDto uSent = new UserSyncDto("u-sent", "alice", "hash-1", List.of("PLAYER"));
        UserSyncDto uPending = new UserSyncDto("u-pending", "bob", "hash-2", List.of("PLAYER"));
        OutboxEventJpaEntity sent = event("e-sent", "SENT", uSent);
        OutboxEventJpaEntity pending = event("e-pending", "PENDING", uPending);

        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(sent, pending));
        when(replicationProgressRepository.existsByEventIdAndServerId(any(), any())).thenReturn(false);
        when(pushUserToLocalServersPort.pushUsers(any(), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("x", true, null)));

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        // Both events replayed as per-event pushes (List.of(user) each).
        verify(pushUserToLocalServersPort).pushUsers(eq(List.of(uSent)), any(RegisteredLocalServer.class));
        verify(pushUserToLocalServersPort).pushUsers(eq(List.of(uPending)), any(RegisteredLocalServer.class));
        // Progress recorded per-event via the domain port.
        verify(replicationProgressRepository).save(new ReplicationProgress("e-sent", "building-1"));
        verify(replicationProgressRepository).save(new ReplicationProgress("e-pending", "building-1"));
    }

    @Test
    void duplicateProgressDiveIsTolerated() throws Exception {
        UserSyncDto u1 = new UserSyncDto("u1", "alice", "hash-1", List.of("PLAYER"));
        OutboxEventJpaEntity e1 = event("e1", "SENT", u1);

        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(e1));
        when(replicationProgressRepository.existsByEventIdAndServerId("e1", "building-1")).thenReturn(false);
        when(pushUserToLocalServersPort.pushUsers(any(), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("u1", true, null)));
        // save hits the (event_id, server_id) unique key — a prior run already recorded progress.
        doThrow(new DataIntegrityViolationException("Duplicate (event_id, server_id)"))
                .when(replicationProgressRepository).save(any());

        // DIVE must be swallowed — catch-up returns normally, event is NOT marked FAILED.
        assertThatCode(() -> service.catchUpNewlyRegisteredServer(newServer("building-1")))
                .doesNotThrowAnyException();
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void emptyReplayListIsNoOp() {
        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of());

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        verifyNoInteractions(pushUserToLocalServersPort);
        verifyNoInteractions(replicationProgressRepository);
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void poisonUserIsMarkedFailedAndOthersProceed() throws Exception {
        UserSyncDto uPoison = new UserSyncDto("u-poison", "", "hash-bad", List.of("PLAYER"));
        UserSyncDto uGood = new UserSyncDto("u-good", "carol", "hash-good", List.of("PLAYER"));
        OutboxEventJpaEntity poison = event("e-poison", "SENT", uPoison);
        OutboxEventJpaEntity good = event("e-good", "PENDING", uGood);

        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(poison, good));
        when(replicationProgressRepository.existsByEventIdAndServerId(any(), any())).thenReturn(false);
        // First event (poison) → VALIDATION_ERROR ack; second event (good) → applied=true ack.
        when(pushUserToLocalServersPort.pushUsers(eq(List.of(uPoison)), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("u-poison", false, "VALIDATION_ERROR: Username cannot be null or empty")));
        when(pushUserToLocalServersPort.pushUsers(eq(List.of(uGood)), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("u-good", true, null)));

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        // Poison event quarantined; NO progress recorded for it.
        verify(outboxEventRepository).markAsFailed("e-poison");
        verify(replicationProgressRepository, never()).save(new ReplicationProgress("e-poison", "building-1"));
        // Good event still proceeds: pushed + progress recorded.
        verify(replicationProgressRepository).save(new ReplicationProgress("e-good", "building-1"));
        verify(outboxEventRepository, never()).markAsFailed("e-good");
    }
}
