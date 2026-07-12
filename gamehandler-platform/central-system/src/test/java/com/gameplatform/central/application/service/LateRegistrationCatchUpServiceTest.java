package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushGameDefinitionToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentMatchToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
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
 * FASE 4 — step 3 unit tests for {@link LateRegistrationCatchUpService}.
 *
 * <p>Adapted for R1+M3: the service now injects the domain
 * {@link ReplicationProgressRepository} port (not the JPA repo), replays
 * SENT+PENDING, pushes per-event, consumes {@link UserSyncAckDto} acks and
 * records {@code replication_progress} per-event.</p>
 */
@ExtendWith(MockitoExtension.class)
class LateRegistrationCatchUpServiceTest {

    @Mock private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PushUserToLocalServersPort pushUserToLocalServersPort;
    @Mock private PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    @Mock private PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    @Mock private PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;
    @Mock private TournamentMatchRepository tournamentMatchRepository;
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
                pushGameDefinitionToLocalServersPort,
                pushTournamentMatchToLocalServersPort,
                tournamentMatchRepository);
    }

    private OutboxEventJpaEntity sentUserEvent(String id, UserSyncDto dto) throws Exception {
        return new OutboxEventJpaEntity(
                id,
                "USER_REGISTERED",
                objectMapper.writeValueAsString(dto),
                "SENT",
                Instant.parse("2026-07-05T00:00:00Z"),
                Instant.parse("2026-07-05T00:00:05Z"));
    }

    private RegisteredLocalServer newServer(String buildingId) {
        return new RegisteredLocalServer(
                new BuildingId(buildingId),
                "https://" + buildingId + ":8081",
                Instant.parse("2026-07-05T12:00:00Z"),
                true);
    }

    @Test
    void catchUpPushesAllSentUserEventsNotInReplicationProgress() throws Exception {
        UserSyncDto u1 = new UserSyncDto("u1", "alice", "hash-1", List.of("PLAYER"));
        UserSyncDto u2 = new UserSyncDto("u2", "bob",   "hash-2", List.of("PLAYER"));
        UserSyncDto u3 = new UserSyncDto("u3", "carol", "hash-3", List.of("ADMIN"));

        OutboxEventJpaEntity e1 = sentUserEvent("e1", u1);
        OutboxEventJpaEntity e2 = sentUserEvent("e2", u2);
        OutboxEventJpaEntity e3 = sentUserEvent("e3", u3);

        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(e1, e2, e3));

        // e2 already replicated for building-1 → exists pre-check true → skipped (no push).
        when(replicationProgressRepository.existsByEventIdAndServerId("e1", "building-1")).thenReturn(false);
        when(replicationProgressRepository.existsByEventIdAndServerId("e2", "building-1")).thenReturn(true);
        when(replicationProgressRepository.existsByEventIdAndServerId("e3", "building-1")).thenReturn(false);

        when(pushUserToLocalServersPort.pushUsers(any(), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("x", true, null)));

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        // Per-event push: e1 and e3 pushed (List.of(user) each), e2 skipped via pre-check.
        verify(pushUserToLocalServersPort).pushUsers(eq(List.of(u1)), any(RegisteredLocalServer.class));
        verify(pushUserToLocalServersPort).pushUsers(eq(List.of(u3)), any(RegisteredLocalServer.class));
        verify(pushUserToLocalServersPort, never()).pushUsers(eq(List.of(u2)), any(RegisteredLocalServer.class));
        // Progress recorded per-event for e1 and e3 only.
        verify(replicationProgressRepository).save(new com.gameplatform.central.domain.model.ReplicationProgress("e1", "building-1"));
        verify(replicationProgressRepository).save(new com.gameplatform.central.domain.model.ReplicationProgress("e3", "building-1"));
        verify(replicationProgressRepository, never()).save(new com.gameplatform.central.domain.model.ReplicationProgress("e2", "building-1"));
    }

    @Test
    void catchUpNoOpWhenNoSentUserEvents() {
        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of());

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        verifyNoInteractions(pushUserToLocalServersPort);
        verifyNoInteractions(replicationProgressRepository);
    }

    @Test
    void catchUpSwallowsPushFailureAndDoesNotThrow() throws Exception {
        UserSyncDto u1 = new UserSyncDto("u1", "alice", "hash-1", List.of("PLAYER"));
        OutboxEventJpaEntity e1 = sentUserEvent("e1", u1);

        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(e1));
        when(replicationProgressRepository.existsByEventIdAndServerId("e1", "building-1")).thenReturn(false);

        doThrow(new RuntimeException("push failed"))
                .when(pushUserToLocalServersPort)
                .pushUsers(any(), any(RegisteredLocalServer.class));

        // Best-effort contract: registration must still succeed even if catch-up push fails.
        assertThatCode(() -> service.catchUpNewlyRegisteredServer(newServer("building-1")))
                .doesNotThrowAnyException();
    }
}
