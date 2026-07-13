package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushGameDefinitionToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushLocalServerRegistryToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTeamMembersToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentMatchToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentParticipantsToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentStandingsToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentSummaryToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserReplicationSchedulerService}, covering:
 * <ul>
 *   <li>Server-failure isolation: one failing server does NOT abort the loop</li>
 *   <li>markAsSent only called when ALL servers received the event</li>
 *   <li>Uses {@code findPendingLimit(50)} instead of {@code findPending()}</li>
 *   <li>No-op when there are no pending events or no active servers</li>
 *   <li>C-R4: per-server push runs in parallel on the {@code replicationPushExecutor}</li>
 * </ul>
 *
 * <p>The deterministic tests (everything except the dedicated parallel test) inject a
 * <em>direct</em> executor ({@code Runnable::run}) so {@code runAsync(task, executor)}
 * runs the task inline on the calling thread. That keeps execution single-threaded and
 * the existing {@code verify(...).pushUsers(...)} asserts stay valid, because
 * {@code allOf().join()} returns immediately while every side effect has already
 * happened.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserReplicationSchedulerServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    @Mock
    private PushUserToLocalServersPort pushUserToLocalServersPort;

    @Mock
    private PushMetadataToLocalServersPort pushMetadataToLocalServersPort;

    @Mock
    private PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;

    @Mock
    private ReplicationProgressRepository replicationProgressRepository;

    @Mock
    private PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;

    @Mock
    private PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort;

    @Mock
    private PushTournamentStandingsToLocalServersPort pushTournamentStandingsToLocalServersPort;

    @Mock
    private PushTournamentParticipantsToLocalServersPort pushTournamentParticipantsToLocalServersPort;

    @Mock
    private PushTeamMembersToLocalServersPort pushTeamMembersToLocalServersPort;

    @Mock
    private PushLocalServerRegistryToLocalServersPort pushLocalServerRegistryToLocalServersPort;

    @Mock
    private TournamentBuildingRepository tournamentBuildingRepository;

    @Mock
    private TournamentMatchRepository tournamentMatchRepository;

    private UserReplicationSchedulerService schedulerService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    /** Direct executor used by the deterministic tests: runs the task on the calling thread. */
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        schedulerService = new UserReplicationSchedulerService(
                outboxEventRepository,
                localServerRegistryPort,
                pushUserToLocalServersPort,
                replicationProgressRepository,
                objectMapper,
                directExecutor,
                pushMetadataToLocalServersPort,
                pushGameDefinitionToLocalServersPort,
                pushTournamentMatchToLocalServersPort,
                tournamentBuildingRepository,
                tournamentMatchRepository,
                pushTournamentSummaryToLocalServersPort,
                pushTournamentStandingsToLocalServersPort,
                pushTournamentParticipantsToLocalServersPort,
                pushTeamMembersToLocalServersPort,
                pushLocalServerRegistryToLocalServersPort
        );
        lenient().when(replicationProgressRepository.findByEventId(any())).thenReturn(List.of());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Uses findPendingLimit, not findPending
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldCallFindPendingLimit_notFindPending() {
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(outboxEventRepository).findPendingLimit(50);
        verify(outboxEventRepository, never()).findPending();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // No-op cases
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldDoNothing_whenNoPendingEvents() {
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    @Test
    void replicateUsers_shouldDoNothing_whenNoActiveServers() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Happy path – all servers succeed → markAsSent
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldMarkAsSent_whenAllServersReceiveEventSuccessfully() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("server-1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("server-2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        when(pushUserToLocalServersPort.pushUsers(any(), any()))
                .thenReturn(List.of(new UserSyncAckDto("ack", true, null)));

        schedulerService.replicateUsers();

        verify(outboxEventRepository).markAsSent(event.getId());
    }

    @Test
    void replicateUsers_shouldPushToEachServer() {
        OutboxEvent event = buildUserEvent("USER_UPDATED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        when(pushUserToLocalServersPort.pushUsers(any(), any()))
                .thenReturn(List.of(new UserSyncAckDto("ack", true, null)));

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Server failure isolation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_shouldContinueToNextServer_whenOneServerFails() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 fails with a RuntimeException; server2 should still receive the event
        doThrow(new RuntimeException("Network error")).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server2)))
                .thenReturn(List.of(new UserSyncAckDto("ack", true, null)));

        schedulerService.replicateUsers();

        // Server2 must still have been called despite server1's failure
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));
    }

    @Test
    void replicateUsers_shouldNotMarkAsSent_whenAtLeastOneServerFails() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 fails, server2 succeeds
        doThrow(new RuntimeException("Timeout")).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server2)))
                .thenReturn(List.of(new UserSyncAckDto("ack", true, null)));

        schedulerService.replicateUsers();

        // Event must NOT be marked as sent because server1 failed
        verify(outboxEventRepository, never()).markAsSent(event.getId());
    }

    @Test
    void replicateUsers_shouldProcessAllEvents_evenWhenSomeServersFail() {
        OutboxEvent event1 = buildUserEvent("USER_REGISTERED");
        OutboxEvent event2 = buildUserEvent("USER_UPDATED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event1, event2));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));

        // First event: server1 fails
        doThrow(new RuntimeException("Timeout"))
                .doReturn(List.of(new UserSyncAckDto("ack", true, null)))   // Second event: server1 succeeds
                .when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));

        schedulerService.replicateUsers();

        // event1 not marked (failed), event2 must be marked
        verify(outboxEventRepository, never()).markAsSent(event1.getId());
        verify(outboxEventRepository).markAsSent(event2.getId());
    }

    @Test
    void replicateUsers_shouldIgnoreNonUserReplicationEvents() {
        // A GAME_SESSION_COMPLETED event must not be replicated by this scheduler
        OutboxEvent irrelevantEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED",
                "{\"dummy\":true}",
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(irrelevantEvent));

        schedulerService.replicateUsers();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    @Test
    void replicateUsers_shouldSkipServerAndNotPush_whenAlreadyReplicatedToThatServer() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // Mock progress for s1, so only s2 needs replication
        ReplicationProgress progress1 = new ReplicationProgress(event.getId(), "s1");
        when(replicationProgressRepository.findByEventId(event.getId())).thenReturn(List.of(progress1));

        schedulerService.replicateUsers();

        // pushUsers should only be called for server2, not server1
        verify(pushUserToLocalServersPort, never()).pushUsers(any(), eq(server1));
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));

        // replicationProgressRepository should only save progress for server2
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // C-R5: duplicate replication_progress insert (DIVE) is treated as success
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_treatsDuplicateProgressAsSuccessAndStillMarksSent() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        // save hits the (event_id, server_id) unique key — a prior run already recorded progress
        doThrow(new DataIntegrityViolationException("Duplicate (event_id, server_id)"))
                .when(replicationProgressRepository).save(any());

        schedulerService.replicateUsers();

        // Push was attempted and (by default mock) succeeded
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        verify(replicationProgressRepository).save(any());
        // DIVE on save must NOT flip allSucceeded → event still transitions to SENT
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    @Test
    void replicateUsers_usesExistsByEventIdAndServerIdPreCheck() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        // Pre-check finds progress already recorded → save is skipped (reduces log noise)
        when(replicationProgressRepository.existsByEventIdAndServerId(event.getId(), "s1")).thenReturn(true);

        schedulerService.replicateUsers();

        // Push still runs (idempotent), but no redundant save is attempted
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        verify(replicationProgressRepository, never()).save(any());
        // Progress already recorded → event is marked sent
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // M3: per-user ACK contract end-to-end (applied / STALE_EVENT / VALIDATION_ERROR)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_acksAppliedTrue_recordsProgress() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server1)))
                .thenReturn(List.of(new UserSyncAckDto("u-ack", true, null)));

        schedulerService.replicateUsers();

        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        // allSucceeded NOT flipped → event transitions to SENT
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateUsers_acksStaleEvent_recordsProgress() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        // Stale events are deliberately skipped on the local side but treated as success
        // for progress purposes (applied=true, reason=STALE_EVENT).
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server1)))
                .thenReturn(List.of(new UserSyncAckDto("u-ack", true, "STALE_EVENT")));

        schedulerService.replicateUsers();

        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateUsers_acksValidationError_marksFailedSkipsSentAndDoesNotRecordProgress() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        // Poison user rejected by the local server.
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server1)))
                .thenReturn(List.of(new UserSyncAckDto("u-ack", false, "VALIDATION_ERROR: blank username")));

        schedulerService.replicateUsers();

        // Poison isolation: event is quarantined, NO progress recorded, allSucceeded NOT flipped.
        verify(replicationProgressRepository, never()).save(any());
        verify(outboxEventRepository).markAsFailed(event.getId());
        verify(outboxEventRepository, never()).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // C-R4: per-server push runs in parallel on replicationPushExecutor
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateUsers_pushesToAllServersInParallelAndDoesNotBlockOnSlowServer() throws Exception {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 push blocks until the test releases the latch (simulates a slow server).
        CountDownLatch server1Block = new CountDownLatch(1);
        doAnswer(invocation -> {
            server1Block.await(5, TimeUnit.SECONDS);
            return null;
        }).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));

        // server2 push completes immediately; its worker then calls save (default existsBy=false).
        when(pushUserToLocalServersPort.pushUsers(any(), eq(server2)))
                .thenReturn(List.of(new UserSyncAckDto("ack", true, null)));

        // Real 2-thread executor so the two pushes actually run concurrently.
        ExecutorService pushExecutor = Executors.newFixedThreadPool(2);
        try {
            UserReplicationSchedulerService parallelService = new UserReplicationSchedulerService(
                    outboxEventRepository,
                    localServerRegistryPort,
                    pushUserToLocalServersPort,
                    replicationProgressRepository,
                    objectMapper,
                    pushExecutor,
                    pushMetadataToLocalServersPort,
                    pushGameDefinitionToLocalServersPort,
                    pushTournamentMatchToLocalServersPort,
                    tournamentBuildingRepository,
                    tournamentMatchRepository,
                    pushTournamentSummaryToLocalServersPort
            );

            // Drive replicateUsers() on its own thread so the test thread can observe
            // progress while allOf().join() is still blocked on server1.
            ExecutorService driver = Executors.newSingleThreadExecutor();
            try {
                driver.submit(parallelService::replicateUsers);

                // server2 must reach save() while server1 is still blocked on its latch.
                // The only way this verify succeeds within the timeout is if server2 ran
                // in parallel with (rather than serially after) server1.
                verify(replicationProgressRepository, timeout(5000))
                        .save(new ReplicationProgress(event.getId(), "s2"));
                // Sanity check: server1 is STILL blocked → confirms server2 made progress
                // without waiting for the slow server.
                assertThat(server1Block.getCount()).as("server1 should still be blocked").isEqualTo(1);

                // Release server1 so allOf().join() can complete and markAsSent runs.
                server1Block.countDown();
            } finally {
                driver.shutdown();
                assertThat(driver.awaitTermination(5, TimeUnit.SECONDS))
                        .as("replicateUsers driver should terminate").isTrue();
            }
        } finally {
            pushExecutor.shutdown();
            assertThat(pushExecutor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("push executor should terminate").isTrue();
        }

        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server1));
        verify(pushUserToLocalServersPort).pushUsers(any(), eq(server2));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        // Both servers ultimately succeeded → event transitions to SENT.
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.5: TOURNAMENT_SUMMARY_UPSERTED drain — pushes to ALL active servers,
    //       replication_progress per (eventId, serverId), markAsSent on all-succeeded,
    //       single-server failure does NOT block others (no markAsSent).
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateTournamentSummaryEvent_pushesToAllActiveServersAndMarksSent_whenAllSucceed() {
        OutboxEvent event = buildTournamentSummaryEvent("t-1", false);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        schedulerService.replicateUsers();

        // Pushed to BOTH servers (broadcast, not building-routed like TOURNAMENT_MATCH_SCHEDULED)
        verify(pushTournamentSummaryToLocalServersPort).push(any(), eq(server1));
        verify(pushTournamentSummaryToLocalServersPort).push(any(), eq(server2));
        // replication_progress recorded for BOTH (eventId, serverId) pairs
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        // allSucceeded → markAsSent
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentSummaryEvent_doesNotMarkAsSent_andStillPushesOthers_whenOneServerFails() {
        OutboxEvent event = buildTournamentSummaryEvent("t-1", false);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        // server1 push throws — server2 must still be pushed (failure isolation)
        doThrow(new RuntimeException("Network error"))
                .when(pushTournamentSummaryToLocalServersPort).push(any(), eq(server1));

        schedulerService.replicateUsers();

        // server2 still received the event despite server1's failure
        verify(pushTournamentSummaryToLocalServersPort).push(any(), eq(server2));
        // server2 progress was recorded (the failing server1's progress is NOT recorded — push failed before save)
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
        // allSucceeded flipped → markAsSent NOT called; markAsFailed NOT called (no poison-isolation, just retry on next tick)
        verify(outboxEventRepository, never()).markAsSent(any());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentSummaryEvent_skipsAlreadyReplicatedServer() {
        OutboxEvent event = buildTournamentSummaryEvent("t-1", false);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        // s1 already replicated — only s2 needs a push
        ReplicationProgress progress1 = new ReplicationProgress(event.getId(), "s1");
        when(replicationProgressRepository.findByEventId(event.getId())).thenReturn(List.of(progress1));

        schedulerService.replicateUsers();

        verify(pushTournamentSummaryToLocalServersPort, never()).push(any(), eq(server1));
        verify(pushTournamentSummaryToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    @Test
    void replicateTournamentSummaryEvent_marksFailed_whenPayloadIsMalformed() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID().toString(),
                "TOURNAMENT_SUMMARY_UPSERTED",
                "{not valid json}",
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );
        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers())
                .thenReturn(List.of(buildServer("s1", "http://s1:8080")));

        schedulerService.replicateUsers();

        verify(outboxEventRepository).markAsFailed(event.getId());
        verify(pushTournamentSummaryToLocalServersPort, never()).push(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.5: TOURNAMENT_STANDINGS_UPSERTED drain — structural twin of the
    //        TOURNAMENT_SUMMARY_UPSERTED drain but pushing TournamentStandingsEventDto.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateTournamentStandingsEvent_pushesToAllActiveServersAndMarksSent_whenAllSucceed() {
        OutboxEvent event = buildTournamentStandingsEvent("t-1");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        schedulerService.replicateUsers();

        verify(pushTournamentStandingsToLocalServersPort).push(any(), eq(server1));
        verify(pushTournamentStandingsToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentStandingsEvent_leavesEventPending_whenNoActiveServers() {
        OutboxEvent event = buildTournamentStandingsEvent("t-1");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushTournamentStandingsToLocalServersPort, never()).push(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentStandingsEvent_skipsAlreadyReplicatedServer() {
        OutboxEvent event = buildTournamentStandingsEvent("t-1");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        ReplicationProgress progress1 = new ReplicationProgress(event.getId(), "s1");
        when(replicationProgressRepository.findByEventId(event.getId())).thenReturn(List.of(progress1));

        schedulerService.replicateUsers();

        verify(pushTournamentStandingsToLocalServersPort, never()).push(any(), eq(server1));
        verify(pushTournamentStandingsToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.5: TOURNAMENT_PARTICIPANTS_UPSERTED drain — structural twin of the
    //        TOURNAMENT_SUMMARY_UPSERTED drain but pushing TournamentParticipantsEventDto.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateTournamentParticipantsEvent_pushesToAllActiveServersAndMarksSent_whenAllSucceed() {
        OutboxEvent event = buildTournamentParticipantsEvent("t-1");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        schedulerService.replicateUsers();

        verify(pushTournamentParticipantsToLocalServersPort).push(any(), eq(server1));
        verify(pushTournamentParticipantsToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentParticipantsEvent_leavesEventPending_whenNoActiveServers() {
        OutboxEvent event = buildTournamentParticipantsEvent("t-1");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        schedulerService.replicateUsers();

        verify(pushTournamentParticipantsToLocalServersPort, never()).push(any(), any());
        verify(outboxEventRepository, never()).markAsSent(any());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateTournamentParticipantsEvent_skipsAlreadyReplicatedServer() {
        OutboxEvent event = buildTournamentParticipantsEvent("t-1");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        ReplicationProgress progress1 = new ReplicationProgress(event.getId(), "s1");
        when(replicationProgressRepository.findByEventId(event.getId())).thenReturn(List.of(progress1));

        schedulerService.replicateUsers();

        verify(pushTournamentParticipantsToLocalServersPort, never()).push(any(), eq(server1));
        verify(pushTournamentParticipantsToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.5: LOCAL_SERVER_REGISTRY_UPSERTED drain — structural twin of the
    //        TOURNAMENT_SUMMARY_UPSERTED drain but pushing LocalServerRegistryEventDto.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateLocalServerRegistryEvent_pushesToAllActiveServersAndMarksSent_whenAllSucceed() {
        OutboxEvent event = buildLocalServerRegistryEvent("b-new", true);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));

        schedulerService.replicateUsers();

        verify(pushLocalServerRegistryToLocalServersPort).push(any(), eq(server1));
        verify(pushLocalServerRegistryToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
        verify(outboxEventRepository, never()).markAsFailed(any());
    }

    @Test
    void replicateLocalServerRegistryEvent_skipsAlreadyReplicatedServer() {
        OutboxEvent event = buildLocalServerRegistryEvent("b-new", true);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");
        RegisteredLocalServer server2 = buildServer("s2", "http://s2:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1, server2));
        ReplicationProgress progress1 = new ReplicationProgress(event.getId(), "s1");
        when(replicationProgressRepository.findByEventId(event.getId())).thenReturn(List.of(progress1));

        schedulerService.replicateUsers();

        verify(pushLocalServerRegistryToLocalServersPort, never()).push(any(), eq(server1));
        verify(pushLocalServerRegistryToLocalServersPort).push(any(), eq(server2));
        verify(replicationProgressRepository, never()).save(new ReplicationProgress(event.getId(), "s1"));
        verify(replicationProgressRepository).save(new ReplicationProgress(event.getId(), "s2"));
        verify(outboxEventRepository).markAsSent(event.getId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Connection-refused deactivation: an unreachable (ConnectException root cause)
    // local server is immediately deactivated so subsequent ticks skip it, instead of
    // spamming ERROR for 15 minutes until the health monitor would catch up.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void replicateLocalServerRegistryEvent_deactivatesServer_whenConnectionRefused() {
        OutboxEvent event = buildLocalServerRegistryEvent("b-new", true);
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));

        RuntimeException connRefused = new RuntimeException("push failed",
                new TransientPushException("transient",
                        new ResourceAccessException("I/O error",
                                new ConnectException("Connection refused"))));
        doThrow(connRefused).when(pushLocalServerRegistryToLocalServersPort).push(any(), eq(server1));

        schedulerService.replicateUsers();

        verify(localServerRegistryPort).deactivate(server1.getBuildingId());
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    @Test
    void replicateUsers_doesNotDeactivateServer_whenFailureIsNotConnectionRefused() {
        OutboxEvent event = buildUserEvent("USER_REGISTERED");
        RegisteredLocalServer server1 = buildServer("s1", "http://s1:8080");

        when(outboxEventRepository.findPendingLimit(50)).thenReturn(List.of(event));
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(server1));
        doThrow(new RuntimeException("Timeout")).when(pushUserToLocalServersPort).pushUsers(any(), eq(server1));

        schedulerService.replicateUsers();

        verify(localServerRegistryPort, never()).deactivate(any(BuildingId.class));
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private OutboxEvent buildUserEvent(String eventType) {
        UserSyncDto dto = new UserSyncDto(UUID.randomUUID().toString(), "alice",
                "$2a$10$fakehashedpassword1234567890", List.of("USER"));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new OutboxEvent(
                UUID.randomUUID().toString(),
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );
    }

    private OutboxEvent buildTournamentSummaryEvent(String tournamentId, boolean deleted) {
        TournamentSummaryEventDto dto = new TournamentSummaryEventDto(
                UUID.randomUUID().toString(),
                "TOURNAMENT_SUMMARY_UPSERTED",
                tournamentId,
                "Test Cup",
                GameType.CHESS,
                false,
                1,
                TournamentStatus.DRAFT,
                Instant.parse("2026-08-01T10:00:00Z"),
                null,
                List.of("b-1", "b-2"),
                0,
                Instant.parse("2026-07-12T10:00:00Z"),
                deleted,
                null
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new OutboxEvent(
                dto.eventId(),
                "TOURNAMENT_SUMMARY_UPSERTED",
                payload,
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );
    }

    private RegisteredLocalServer buildServer(String buildingId, String baseUrl) {
        return new RegisteredLocalServer(new BuildingId(buildingId), baseUrl, Instant.now(), true);
    }

    private OutboxEvent buildTournamentStandingsEvent(String tournamentId) {
        TournamentStandingsEventDto dto = new TournamentStandingsEventDto(
                UUID.randomUUID().toString(),
                "TOURNAMENT_STANDINGS_UPSERTED",
                tournamentId,
                List.of(new TournamentStandingDto("u-1", "Alice", 1, 0, 3, 1)),
                null,
                Instant.parse("2026-07-12T10:00:00Z")
        );
        return serializeEvent(dto, "TOURNAMENT_STANDINGS_UPSERTED");
    }

    private OutboxEvent buildTournamentParticipantsEvent(String tournamentId) {
        TournamentParticipantsEventDto dto = new TournamentParticipantsEventDto(
                UUID.randomUUID().toString(),
                "TOURNAMENT_PARTICIPANTS_UPSERTED",
                tournamentId,
                List.of(new TournamentParticipantViewDto("u-1", false, "Alice",
                        Instant.parse("2026-07-12T10:00:00Z"))),
                null,
                Instant.parse("2026-07-12T10:00:00Z")
        );
        return serializeEvent(dto, "TOURNAMENT_PARTICIPANTS_UPSERTED");
    }

    private OutboxEvent buildLocalServerRegistryEvent(String buildingId, boolean active) {
        LocalServerRegistryEventDto dto = new LocalServerRegistryEventDto(
                UUID.randomUUID().toString(),
                "LOCAL_SERVER_REGISTRY_UPSERTED",
                buildingId,
                "http://" + buildingId + ":8080",
                Instant.parse("2026-07-12T10:00:00Z"),
                active,
                Instant.parse("2026-07-12T10:00:00Z")
        );
        return serializeEvent(dto, "LOCAL_SERVER_REGISTRY_UPSERTED");
    }

    private OutboxEvent serializeEvent(Object dto, String eventType) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new OutboxEvent(
                UUID.randomUUID().toString(),
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                Instant.now(),
                null
        );
    }
}
