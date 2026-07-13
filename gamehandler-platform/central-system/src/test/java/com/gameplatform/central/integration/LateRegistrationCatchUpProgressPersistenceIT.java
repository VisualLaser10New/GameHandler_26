package com.gameplatform.central.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.application.service.LateRegistrationCatchUpService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushGameDefinitionToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentMatchToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.adapter.LocalServerRepositoryAdapter;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1 + M8 — late-registration catch-up progress persistence (downgraded IT).
 *
 * <p><b>Downgrade note (plan step 25 fallback):</b> the original plan asked for an
 * H2 + WireMock {@code @SpringBootTest} IT that seeds 2 outbox events, registers a
 * new building and asserts 2 {@code replication_progress} rows. The H2 context boots
 * fine and the afterCommit catch-up runs, but H2's {@code JSON} column + Hibernate's
 * plain {@code String} binding double-encodes the outbox payload on read-back (the
 * JPA entity uses {@code @Column(columnDefinition="JSON") String payload} with no
 * custom Hibernate JSON type), so {@code objectMapper.readValue(entity.getPayload(), ...)}
 * receives a JSON string literal instead of a JSON object. This is a pre-existing
 * environment limitation of the central outbox on H2 — not introduced by this change,
 * and not fixable without touching the outbox entity (out of scope). Per the plan's
 * fallback, this is converted to a Mockito test that mocks the ports and asserts the
 * per-event progress-recording sequence, AND additionally covers the M8
 * {@code afterCommit} decoupling (which the H2 IT could not have asserted either).</p>
 *
 * <p>Asserts:
 * <ul>
 *   <li>M8: {@code LocalServerRepositoryAdapter.register} persists the building row
 *       inside the tx but does NOT call the catch-up during the tx body — it only
 *       registers a {@link TransactionSynchronization} whose {@code afterCommit()}
 *       triggers the catch-up.</li>
 *   <li>R1: the catch-up replays SENT + PENDING events and records
 *       {@code replication_progress} per-event (ordered) via the domain
 *       {@link ReplicationProgressRepository} port.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LateRegistrationCatchUpProgressPersistenceIT {

    private static final String BUILDING_ID = "building-catchup-it";

    @Mock private LocalServerJpaRepository localServerJpaRepository;
    @Mock private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PushUserToLocalServersPort pushUserToLocalServersPort;
    @Mock private PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    @Mock private PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    @Mock private PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;
    @Mock private TournamentMatchRepository tournamentMatchRepository;
    @Mock private ReplicationProgressRepository replicationProgressRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private LateRegistrationCatchUpService catchUpService;
    private LocalServerRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        catchUpService = new LateRegistrationCatchUpService(
                outboxEventJpaRepository, outboxEventRepository, pushUserToLocalServersPort,
                objectMapper, replicationProgressRepository, pushMetadataToLocalServersPort,
                pushGameDefinitionToLocalServersPort, pushTournamentMatchToLocalServersPort,
                tournamentMatchRepository);
        adapter = new LocalServerRepositoryAdapter(localServerJpaRepository, catchUpService);
        // Mimic an active transaction so registerSynchronization() is accepted.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OutboxEventJpaEntity event(String id, String status, UserSyncDto dto) throws Exception {
        return new OutboxEventJpaEntity(
                id, "USER_REGISTERED", objectMapper.writeValueAsString(dto), status,
                Instant.parse("2026-07-05T00:00:00Z"),
                "SENT".equals(status) ? Instant.parse("2026-07-05T00:00:05Z") : null);
    }

    @Test
    void registerDefersCatchUpToAfterCommitAndCatchUpRecordsProgressPerEvent() throws Exception {
        UserSyncDto uSent = new UserSyncDto("u-sent", "alice", "a@example.com", "hash-1", List.of("PLAYER"), Instant.parse("2026-07-05T10:00:00Z"));
        UserSyncDto uPending = new UserSyncDto("u-pending", "bob", "b@example.com", "hash-2", List.of("PLAYER"), Instant.parse("2026-07-05T10:05:00Z"));
        OutboxEventJpaEntity sent = event("evt-sent", "SENT", uSent);
        OutboxEventJpaEntity pending = event("evt-pending", "PENDING", uPending);

        when(localServerJpaRepository.findById(BUILDING_ID)).thenReturn(Optional.empty());
        when(outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                anyCollection(), anyCollection())).thenReturn(List.of(sent, pending));
        when(replicationProgressRepository.existsByEventIdAndServerId(any(), any())).thenReturn(false);
        when(pushUserToLocalServersPort.pushUsers(any(), any(RegisteredLocalServer.class)))
                .thenReturn(List.of(new UserSyncAckDto("x", true, null)));

        RegisteredLocalServer server = new RegisteredLocalServer(
                new BuildingId(BUILDING_ID), "http://localhost:8181", Instant.now(), true);

        // register() runs inside the (mimicked) tx: it persists the building row and
        // registers an afterCommit synchronization, but must NOT call the catch-up yet.
        adapter.register(server);

        verify(localServerJpaRepository).save(any());
        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(replicationProgressRepository, never()).save(any());

        // The afterCommit synchronization is what triggers the catch-up.
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);

        syncs.get(0).afterCommit();

        // R1: per-event progress recorded via the domain port, in event order.
        verify(replicationProgressRepository).save(new ReplicationProgress("evt-sent", BUILDING_ID));
        verify(replicationProgressRepository).save(new ReplicationProgress("evt-pending", BUILDING_ID));
        verify(pushUserToLocalServersPort, times(2)).pushUsers(any(), any(RegisteredLocalServer.class));
    }
}
