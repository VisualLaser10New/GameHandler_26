package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ReplicationProgressJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FASE 4 — step 3 unit tests for {@link LateRegistrationCatchUpService}.
 *
 * <p>Pure Mockito unit tests (no Spring context). {@link ObjectMapper} is a
 * real {@code new ObjectMapper()} so JSON (de)serialization of {@link UserSyncDto}
 * payloads works the same way as in production; the three repository / port
 * collaborators are mocked.</p>
 *
 * <ul>
 *   <li>{@code catchUpPushesAllSentUserEventsNotInReplicationProgress} — 3 SENT
 *       USER events, e2 already replicated → push exactly 2 (e1, e3).</li>
 *   <li>{@code catchUpNoOpWhenNoSentUserEvents} — empty SENT list → no push,
 *       no replication_progress query.</li>
 *   <li>{@code catchUpSwallowsPushFailureAndDoesNotThrow} — pushUsers throws;
 *       best-effort contract: service returns normally (registration must
 *       still succeed).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LateRegistrationCatchUpServiceTest {

    @Mock private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock private PushUserToLocalServersPort pushUserToLocalServersPort;
    @Mock private ReplicationProgressJpaRepository replicationProgressJpaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LateRegistrationCatchUpService service;

    @BeforeEach
    void setUp() {
        service = new LateRegistrationCatchUpService(
                outboxEventJpaRepository,
                pushUserToLocalServersPort,
                objectMapper,
                replicationProgressJpaRepository);
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

        when(outboxEventJpaRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                eq("SENT"), anyCollection())).thenReturn(List.of(e1, e2, e3));

        // e2 already replicated for building-1 — must be filtered out.
        ReplicationProgressJpaEntity progressForE2 =
                new ReplicationProgressJpaEntity("rp-e2-building-1", "e2", "building-1");
        when(replicationProgressJpaRepository.findByEventIdInAndServerId(
                eq(List.of("e1", "e2", "e3")), eq("building-1")))
                .thenReturn(List.of(progressForE2));

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        // Pushed list must contain only e1 and e3 (e2 dedup'd via replication_progress).
        verify(pushUserToLocalServersPort).pushUsers(
                argThat((List<UserSyncDto> pushed) -> pushed != null
                        && pushed.size() == 2
                        && pushed.get(0).userId().equals("u1")
                        && pushed.get(1).userId().equals("u3")),
                any(RegisteredLocalServer.class));
    }

    @Test
    void catchUpNoOpWhenNoSentUserEvents() {
        when(outboxEventJpaRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                eq("SENT"), anyCollection())).thenReturn(List.of());

        service.catchUpNewlyRegisteredServer(newServer("building-1"));

        verifyNoInteractions(pushUserToLocalServersPort);
        verifyNoInteractions(replicationProgressJpaRepository);
    }

    @Test
    void catchUpSwallowsPushFailureAndDoesNotThrow() throws Exception {
        UserSyncDto u1 = new UserSyncDto("u1", "alice", "hash-1", List.of("PLAYER"));
        OutboxEventJpaEntity e1 = sentUserEvent("e1", u1);

        when(outboxEventJpaRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                eq("SENT"), anyCollection())).thenReturn(List.of(e1));
        when(replicationProgressJpaRepository.findByEventIdInAndServerId(
                eq(List.of("e1")), eq("building-1")))
                .thenReturn(List.of());

        doThrow(new RuntimeException("push failed"))
                .when(pushUserToLocalServersPort)
                .pushUsers(anyList(), any(RegisteredLocalServer.class));

        // Best-effort contract: registration must still succeed even if catch-up push fails.
        assertThatCode(() -> service.catchUpNewlyRegisteredServer(newServer("building-1")))
                .doesNotThrowAnyException();
    }
}
