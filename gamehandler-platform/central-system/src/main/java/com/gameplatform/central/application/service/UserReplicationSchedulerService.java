package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushGameDefinitionToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushLocalServerRegistryToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentMatchToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentParticipantsToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTeamMembersToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentStandingsToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushTournamentSummaryToLocalServersPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import com.gameplatform.shared.dto.TeamMembersEventDto;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Scheduled service that replicates pending user events to all active local servers.
 *
 * <p>Resilience: if pushing to one server fails, the error is logged and the loop
 * continues to the next server – a single failing server never aborts the entire batch.</p>
 *
 * <p>Correctness: an event is only marked as SENT when it has been successfully pushed
 * to <em>every</em> active local server in the current run.</p>
 *
 * <p>Backpressure: events are fetched in chunks of {@value #BATCH_SIZE} to keep memory
 * usage bounded regardless of queue depth.</p>
 *
 * <p>Uses {@code fixedDelay} so the next execution only starts after the previous one
 * completes, preventing overlapping scheduler runs.</p>
 *
 * <p>Parallelism (C-R4): for a given event, the push to every active local server is
 * dispatched concurrently on the {@code replicationPushExecutor} executor and joined
 * with {@code CompletableFuture.allOf().join()} before {@code markAsSent} /
 * {@code markAsFailed} run on the scheduler thread. As a result a slow or unreachable
 * server no longer blocks replication to the remaining healthy servers, and the
 * scheduler method still behaves synchronously from the caller's perspective.</p>
 */
@Service
public class UserReplicationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(UserReplicationSchedulerService.class);

    private static final String USER_REGISTERED_EVENT = "USER_REGISTERED";
    private static final String USER_UPDATED_EVENT    = "USER_UPDATED";
    private static final String LOCAL_ADMIN_BUILDING_ASSIGNED_EVENT = "LOCAL_ADMIN_BUILDING_ASSIGNED";
    private static final String LOCAL_ADMIN_BUILDING_REVOKED_EVENT    = "LOCAL_ADMIN_BUILDING_REVOKED";
    private static final String GAME_DEFINITION_UPSERTED_EVENT = "GAME_DEFINITION_UPSERTED";
    private static final String TOURNAMENT_MATCH_SCHEDULED_EVENT = "TOURNAMENT_MATCH_SCHEDULED";
    private static final String TOURNAMENT_SUMMARY_UPSERTED_EVENT = "TOURNAMENT_SUMMARY_UPSERTED";
    private static final String TOURNAMENT_STANDINGS_UPSERTED_EVENT = "TOURNAMENT_STANDINGS_UPSERTED";
    private static final String TOURNAMENT_PARTICIPANTS_UPSERTED_EVENT = "TOURNAMENT_PARTICIPANTS_UPSERTED";
    private static final String LOCAL_SERVER_REGISTRY_UPSERTED_EVENT = "LOCAL_SERVER_REGISTRY_UPSERTED";
    private static final String TEAM_MEMBERS_UPSERTED_EVENT = "TEAM_MEMBERS_UPSERTED";
    /** Maximum number of pending events to fetch per scheduler run. */
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final LocalServerRegistryPort localServerRegistryPort;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    private final PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    private final ReplicationProgressRepository replicationProgressRepository;
    private final ObjectMapper objectMapper;
    /**
     * Dedicated executor (bean {@code replicationPushExecutor}) used to push a
     * single user-replication event to all active local servers in parallel.
     * Kept separate from the {@code TaskScheduler} so blocking REST I/O never
     * starves {@code @Scheduled} methods.
     */
    private final Executor replicationPushExecutor;
    private final PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;
    private final TournamentBuildingRepository tournamentBuildingRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort;
    private final PushTournamentStandingsToLocalServersPort pushTournamentStandingsToLocalServersPort;
    private final PushTournamentParticipantsToLocalServersPort pushTournamentParticipantsToLocalServersPort;
    private final PushTeamMembersToLocalServersPort pushTeamMembersToLocalServersPort;
    private final PushLocalServerRegistryToLocalServersPort pushLocalServerRegistryToLocalServersPort;

    @org.springframework.beans.factory.annotation.Autowired
    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper,
            @Qualifier("replicationPushExecutor") Executor replicationPushExecutor,
            PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
            PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
            PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
            TournamentBuildingRepository tournamentBuildingRepository,
            TournamentMatchRepository tournamentMatchRepository,
            PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort,
            PushTournamentStandingsToLocalServersPort pushTournamentStandingsToLocalServersPort,
            PushTournamentParticipantsToLocalServersPort pushTournamentParticipantsToLocalServersPort,
            PushTeamMembersToLocalServersPort pushTeamMembersToLocalServersPort,
            PushLocalServerRegistryToLocalServersPort pushLocalServerRegistryToLocalServersPort
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.localServerRegistryPort = localServerRegistryPort;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.replicationProgressRepository = replicationProgressRepository;
        this.objectMapper = objectMapper;
        this.replicationPushExecutor = replicationPushExecutor;
        this.pushMetadataToLocalServersPort = pushMetadataToLocalServersPort;
        this.pushGameDefinitionToLocalServersPort = pushGameDefinitionToLocalServersPort;
        this.pushTournamentMatchToLocalServersPort = pushTournamentMatchToLocalServersPort;
        this.tournamentBuildingRepository = tournamentBuildingRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.pushTournamentSummaryToLocalServersPort = pushTournamentSummaryToLocalServersPort;
        this.pushTournamentStandingsToLocalServersPort = pushTournamentStandingsToLocalServersPort;
        this.pushTournamentParticipantsToLocalServersPort = pushTournamentParticipantsToLocalServersPort;
        this.pushTeamMembersToLocalServersPort = pushTeamMembersToLocalServersPort;
        this.pushLocalServerRegistryToLocalServersPort = pushLocalServerRegistryToLocalServersPort;
    }

    /**
     * Backward-compat legacy ctor (pattern {@code SyncEventProcessor:91-146}):
     * 12-arg delegating to the 16-arg production ctor with {@code null} for the
     * four new FASE 7-A3/S3/BUG-TEAM-3 ports
     * ({@link PushTournamentStandingsToLocalServersPort},
     * {@link PushTournamentParticipantsToLocalServersPort},
     * {@link PushTeamMembersToLocalServersPort},
     * {@link PushLocalServerRegistryToLocalServersPort}). Preserves existing S2
     * tests that still use the 12-arg ctor without stubs for the new ports; the
     * {@code @Autowired} 16-arg ctor remains the production entry point. When
     * any of the four is {@code null}, the corresponding drain branch
     * short-circuits and leaves the event PENDING.
     */
    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper,
            @Qualifier("replicationPushExecutor") Executor replicationPushExecutor,
            PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
            PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
            PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
            TournamentBuildingRepository tournamentBuildingRepository,
            TournamentMatchRepository tournamentMatchRepository,
            PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort
    ) {
        this(outboxEventRepository, localServerRegistryPort, pushUserToLocalServersPort,
                replicationProgressRepository, objectMapper, replicationPushExecutor,
                pushMetadataToLocalServersPort, pushGameDefinitionToLocalServersPort,
                pushTournamentMatchToLocalServersPort, tournamentBuildingRepository,
                tournamentMatchRepository, pushTournamentSummaryToLocalServersPort,
                null, null, null, null);
    }

    /**
     * Backward-compat legacy ctor (pre-S2): 11-arg delegating to the 12-arg
     * legacy ctor with {@code null} for the
     * {@link PushTournamentSummaryToLocalServersPort}. Transitive delegation
     * reaches the 16-arg production ctor with {@code null} for the five S2/S3/BUG-TEAM-3
     * ports.
     */
    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper,
            @Qualifier("replicationPushExecutor") Executor replicationPushExecutor,
            PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
            PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
            PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
            TournamentBuildingRepository tournamentBuildingRepository,
            TournamentMatchRepository tournamentMatchRepository
    ) {
        this(outboxEventRepository, localServerRegistryPort, pushUserToLocalServersPort,
                replicationProgressRepository, objectMapper, replicationPushExecutor,
                pushMetadataToLocalServersPort, pushGameDefinitionToLocalServersPort,
                pushTournamentMatchToLocalServersPort, tournamentBuildingRepository,
                tournamentMatchRepository, null);
    }

    /**
     * Polls for pending user-replication events and pushes them to local servers.
     *
     * <p>{@code fixedDelay} ensures no overlapping runs – the 5-minute window begins
     * only after the previous invocation finishes.</p>
     *
     * <p>C-R4: the per-server push for a single event runs in parallel on
     * {@code replicationPushExecutor}; {@code markAsSent} / {@code markAsFailed}
     * only run on the scheduler thread after {@code allOf().join()} completes,
     * so the method stays synchronous from the caller's perspective and
     * {@code fixedDelay} still guarantees no self-overlap.</p>
     */
    @Scheduled(fixedDelayString = "${app.sync-interval-ms:300000}")
    public void replicateUsers() {
        // Fetch at most BATCH_SIZE events to avoid loading an unbounded result set.
        // D2: the drain now also covers LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED metadata
        // events (filtered in Java alongside user-replication events, so the query is
        // unchanged and the scheduler diff stays minimal).
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingLimit(BATCH_SIZE).stream()
                .filter(this::isReplicationEvent)
                .toList();

        if (pendingEvents.isEmpty()) {
            return;
        }

        List<RegisteredLocalServer> activeLocalServers = localServerRegistryPort.getActiveLocalServers();

        if (activeLocalServers.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            if (isMetadataEvent(event)) {
                replicateMetadataEvent(event, activeLocalServers);
                continue;
            } else if (isGameDefinitionEvent(event)) {
                replicateGameDefinitionEvent(event, activeLocalServers);
                continue;
            } else if (isTournamentMatchEvent(event)) {
                replicateTournamentMatchEvent(event, activeLocalServers);
                continue;
            } else if (isTournamentSummaryEvent(event)) {
                replicateTournamentSummaryEvent(event, activeLocalServers);
                continue;
            } else if (isTournamentStandingsEvent(event)) {
                replicateTournamentStandingsEvent(event, activeLocalServers);
                continue;
            } else if (isTournamentParticipantsEvent(event)) {
                replicateTournamentParticipantsEvent(event, activeLocalServers);
                continue;
            } else if (isLocalServerRegistryEvent(event)) {
                replicateLocalServerRegistryEvent(event, activeLocalServers);
                continue;
            } else if (isTeamMembersEvent(event)) {
                replicateTeamMembersEvent(event, activeLocalServers);
                continue;
            }
            UserSyncDto user;
            try {
                user = deserializeUser(event);
            } catch (Exception e) {
                log.error("Failed to deserialize user replication event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                        event.getId(), event.getPayload(), e);
                try {
                    outboxEventRepository.markAsFailed(event.getId());
                } catch (Exception dbEx) {
                    log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
                }
                continue; // Proceed to process next events in the current batch
            }

            List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
            Set<String> alreadyReplicatedServerIds = progressList.stream()
                    .map(ReplicationProgress::serverId)
                    .collect(Collectors.toSet());

            // Track whether all servers received the event successfully. AtomicBoolean
            // because it is mutated from worker threads dispatched on replicationPushExecutor.
            AtomicBoolean allSucceeded = new AtomicBoolean(true);

            // C-R4: push to every not-yet-replicated server in parallel on the dedicated
            // replicationPushExecutor; allOf().join() makes the method block until every
            // push (and its replication_progress bookkeeping) has completed before the
            // scheduler thread decides whether to markAsSent.
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (RegisteredLocalServer server : activeLocalServers) {
                String serverId = server.getBuildingId().id();
                if (alreadyReplicatedServerIds.contains(serverId)) {
                    continue;
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    // ── C-R5: split try/catch ── push failure is real failure; save failure
                    // (duplicate) is treated as success because a prior run already recorded
                    // the progress.
                    List<UserSyncAckDto> acks;
                    try {
                        acks = pushUserToLocalServersPort.pushUsers(List.of(user), server);
                    } catch (Exception e) {
                        // Isolate per-server push failures: log and continue to the next server
                        allSucceeded.set(false);
                        if (isConnectionRefused(e)) {
                            try {
                                localServerRegistryPort.deactivate(server.getBuildingId());
                            } catch (Exception dex) {
                                log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                            }
                            log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                    server.getBaseUrl());
                        } else {
                            log.error("Failed to push user event [{}] to server [{}]: {}",
                                    event.getId(), server.getBaseUrl(), e.getMessage(), e);
                        }
                        return;
                    }

                    UserSyncAckDto ack = (acks == null || acks.isEmpty()) ? null : acks.get(0);

                    // M3 poison isolation: a poison user (e.g. blank username) is rejected by
                    // the local server. Quarantine THIS event only — flip the per-event
                    // allSucceeded flag so the post-join markAsSent is skipped and markAsFailed
                    // stands. allSucceeded is created fresh per event, so this does NOT
                    // contaminate other events in the same tick. Do NOT record progress.
                    if (ack != null && !ack.applied() && ack.reason() != null
                            && ack.reason().startsWith("VALIDATION_ERROR")) {
                        log.warn("Poison user isolation: eventId={} serverId={} reason={}",
                                event.getId(), serverId, ack.reason());
                        allSucceeded.set(false);
                        try {
                            outboxEventRepository.markAsFailed(event.getId());
                        } catch (Exception dbEx) {
                            log.error("Failed to mark event [{}] as FAILED", event.getId(), dbEx);
                        }
                        return;
                    }

                    // applied=true OR STALE_EVENT OR (no ack body → legacy success) → record progress.
                    if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                        log.info("replication_progress already present (pre-check) for eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    } else {
                        try {
                            replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                        } catch (DataIntegrityViolationException dup) {
                            // Duplicate insert on (event_id, server_id) unique key — a prior run already
                            // recorded the progress after a successful push. Treat as success and
                            // DO NOT flip allSucceeded to false.
                            log.info("replication_progress already present for eventId={}, serverId={} — treating as success",
                                    event.getId(), serverId);
                        }
                    }
                }, replicationPushExecutor));
            }

            // Block the scheduler thread until every parallel push has settled, so the
            // markAsSent decision below is made with the full picture for this event.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Only mark as sent when the event reached every active server.
            // Runs on the scheduler thread, after allOf().join().
            if (allSucceeded.get()) {
                outboxEventRepository.markAsSent(event.getId());
            } else {
                log.warn("User event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
            }
        }
    }

    /**
     * D2 — drains a single {@code LOCAL_ADMIN_BUILDING_ASSIGNED} /
     * {@code LOCAL_ADMIN_BUILDING_REVOKED} metadata event to every active
     * local server in parallel on {@code replicationPushExecutor}, recording
     * {@code replication_progress} per (eventId, serverId) exactly like the
     * user path. There is no ack / poison isolation: metadata upsert/delete is
     * idempotent by composite PK, so a transient failure just leaves the event
     * PENDING (allSucked→false skips markAsSent) for a future tick to retry.
     *
     * <p>Consistent with the USER path, {@code replication_progress} always
     * tracks the outbox event id ({@code event.getId()}), and the
     * {@link LocalAdminBuildingEventDto#eventId()} embedded in the payload is
     * set equal to that same id by the producer.</p>
     */
    private void replicateMetadataEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        LocalAdminBuildingEventDto metadataEvent;
        try {
            metadataEvent = objectMapper.readValue(event.getPayload(), LocalAdminBuildingEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize metadata event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushMetadataToLocalServersPort.pushMetadata(List.of(metadataEvent), server);
                } catch (Exception e) {
                    // No poison isolation for metadata — just log + flip allSucceeded so a
                    // future tick retries. The local upsert/delete is idempotent by PK.
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push metadata event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Metadata event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code GAME_DEFINITION_UPSERTED} event to every active
     * local server in parallel on {@code replicationPushExecutor}, recording
     * {@code replication_progress} per (eventId, serverId) exactly like the
     * user and metadata paths. There is no ack / poison isolation: the local
     * upsert is idempotent by composite PK (game_type), so a transient failure
     * just leaves the event PENDING (allSucceeded=false skips markAsSent) for a
     * future tick to retry.
     *
     * <p>Structural mirror of {@link #replicateMetadataEvent(OutboxEvent, List)}
     * but deserializes {@link GameDefinitionEventDto} and dispatches through
     * {@link PushGameDefinitionToLocalServersPort#pushGameDefinitions}.</p>
     */
    private void replicateGameDefinitionEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        GameDefinitionEventDto gameDefinitionEvent;
        try {
            gameDefinitionEvent = objectMapper.readValue(event.getPayload(), GameDefinitionEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize game-definition event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushGameDefinitionToLocalServersPort.pushGameDefinitions(List.of(gameDefinitionEvent), server);
                } catch (Exception e) {
                    // No poison isolation for game-definition — just log + flip allSucceeded
                    // so a future tick retries. The local upsert is idempotent by PK (game_type).
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push game-definition event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for game-definition eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for game-definition eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Game-definition event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code TOURNAMENT_SUMMARY_UPSERTED} event to every active
     * local server in parallel on {@code replicationPushExecutor}, recording
     * {@code replication_progress} per (eventId, serverId) exactly like the
     * game-definition path. There is no ack / poison isolation: the local
     * upsert is idempotent by PK ({@code tournamentId}), so a transient
     * failure just leaves the event PENDING (allSucceeded=false skips
     * markAsSent) for a future tick to retry. Structural twin of
     * {@link #replicateGameDefinitionEvent(OutboxEvent, List)} but pushing to
     * all active servers (no building routing filter, since the summary
     * projection is global). {@code deleted=true} tombstones are handled by
     * the local side as a {@code deleteById} on the projection.
     *
     * <p>Backward-compat: when the legacy 11-arg ctor is used (no
     * {@link PushTournamentSummaryToLocalServersPort} wired), the method
     * short-circuits and leaves the event PENDING — this lets existing tests
     * that still use the legacy ctor pass without a stub for the new port.</p>
     */
    private void replicateTournamentSummaryEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        if (pushTournamentSummaryToLocalServersPort == null) {
            log.warn("Tournament-summary event [{}] — no PushTournamentSummaryToLocalServersPort wired (legacy ctor) — leaving event PENDING.",
                    event.getId());
            return;
        }
        TournamentSummaryEventDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), TournamentSummaryEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize tournament-summary event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushTournamentSummaryToLocalServersPort.push(List.of(dto), server);
                } catch (Exception e) {
                    // No poison isolation for tournament-summary — just log + flip allSucceeded
                    // so a future tick retries. The local upsert is idempotent by PK (tournamentId).
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push tournament-summary event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for tournament-summary eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for tournament-summary eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Tournament-summary event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code TOURNAMENT_STANDINGS_UPSERTED} event to every active
     * local server in parallel on {@code replicationPushExecutor}, recording
     * {@code replication_progress} per (eventId, serverId). Structural twin of
     * {@link #replicateTournamentSummaryEvent(OutboxEvent, List)} but pushing
     * {@link TournamentStandingsEventDto} via
     * {@link PushTournamentStandingsToLocalServersPort#push}. No ack / poison
     * isolation: the local upsert is a delete+insert snapshot idempotent by
     * {@code tournamentId}. Backward-compat: when the legacy ctor is used (no
     * port wired), the method short-circuits and leaves the event PENDING.
     */
    private void replicateTournamentStandingsEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        if (pushTournamentStandingsToLocalServersPort == null) {
            log.warn("Tournament-standings event [{}] — no PushTournamentStandingsToLocalServersPort wired (legacy ctor) — leaving event PENDING.",
                    event.getId());
            return;
        }
        TournamentStandingsEventDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), TournamentStandingsEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize tournament-standings event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushTournamentStandingsToLocalServersPort.push(List.of(dto), server);
                } catch (Exception e) {
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push tournament-standings event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for tournament-standings eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for tournament-standings eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Tournament-standings event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code TOURNAMENT_PARTICIPANTS_UPSERTED} event to every
     * active local server in parallel on {@code replicationPushExecutor}.
     * Structural twin of {@link #replicateTournamentSummaryEvent(OutboxEvent, List)}
     * but pushing {@link TournamentParticipantsEventDto} via
     * {@link PushTournamentParticipantsToLocalServersPort#push}. No ack / poison
     * isolation: the local upsert is a delete+insert snapshot idempotent by
     * {@code tournamentId}. Backward-compat: when the legacy ctor is used (no
     * port wired), the method short-circuits and leaves the event PENDING.
     */
    private void replicateTournamentParticipantsEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        if (pushTournamentParticipantsToLocalServersPort == null) {
            log.warn("Tournament-participants event [{}] — no PushTournamentParticipantsToLocalServersPort wired (legacy ctor) — leaving event PENDING.",
                    event.getId());
            return;
        }
        TournamentParticipantsEventDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), TournamentParticipantsEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize tournament-participants event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushTournamentParticipantsToLocalServersPort.push(List.of(dto), server);
                } catch (Exception e) {
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push tournament-participants event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for tournament-participants eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for tournament-participants eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Tournament-participants event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code LOCAL_SERVER_REGISTRY_UPSERTED} event to every
     * active local server in parallel on {@code replicationPushExecutor}.
     * Structural twin of {@link #replicateTournamentSummaryEvent(OutboxEvent, List)}
     * but pushing {@link LocalServerRegistryEventDto} via
     * {@link PushLocalServerRegistryToLocalServersPort#push}. No ack / poison
     * isolation: the local upsert is idempotent by PK ({@code buildingId}).
     * Backward-compat: when the legacy ctor is used (no port wired), the method
     * short-circuits and leaves the event PENDING.
     */
    private void replicateLocalServerRegistryEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        if (pushLocalServerRegistryToLocalServersPort == null) {
            log.warn("Local-server-registry event [{}] — no PushLocalServerRegistryToLocalServersPort wired (legacy ctor) — leaving event PENDING.",
                    event.getId());
            return;
        }
        LocalServerRegistryEventDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), LocalServerRegistryEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize local-server-registry event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushLocalServerRegistryToLocalServersPort.push(List.of(dto), server);
                } catch (Exception e) {
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push local-server-registry event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for local-server-registry eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for local-server-registry eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Local-server-registry event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code TEAM_MEMBERS_UPSERTED} event to every active
     * local server in parallel on {@code replicationPushExecutor}. Structural
     * twin of {@link #replicateTournamentParticipantsEvent(OutboxEvent, List)}
     * but pushing {@link TeamMembersEventDto} via
     * {@link PushTeamMembersToLocalServersPort#push}. No ack / poison
     * isolation: the local upsert is a delete+insert snapshot idempotent by
     * {@code tournamentId}. Broadcast (no per-building routing) so every
     * Local hosting any match of the tournament can resolve team→user
     * membership for the {@code myMatches} JPQL join (BUG-TEAM-3).
     * Backward-compat: when the legacy ctor is used (no port wired), the
     * method short-circuits and leaves the event PENDING.
     */
    private void replicateTeamMembersEvent(OutboxEvent event, List<RegisteredLocalServer> activeLocalServers) {
        if (pushTeamMembersToLocalServersPort == null) {
            log.warn("Team-members event [{}] — no PushTeamMembersToLocalServersPort wired (legacy ctor) — leaving event PENDING.",
                    event.getId());
            return;
        }
        TeamMembersEventDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), TeamMembersEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize team-members event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RegisteredLocalServer server : activeLocalServers) {
            String serverId = server.getBuildingId().id();
            if (alreadyReplicatedServerIds.contains(serverId)) {
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushTeamMembersToLocalServersPort.push(List.of(dto), server);
                } catch (Exception e) {
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(server.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", server.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                server.getBaseUrl());
                    } else {
                        log.error("Failed to push team-members event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for team-members eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for team-members eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Team-members event [{}] was NOT marked as sent because one or more servers failed.", event.getId());
        }
    }

    /**
     * Drains a single {@code TOURNAMENT_MATCH_SCHEDULED} event: deserialises
     * the payload to {@link TournamentMatchScheduledDto}, loads the involved
     * buildings via {@code tournamentBuildingRepository.findByTournament(tournamentId)},
     * round-robin-assigns a {@code buildingId} to the central
     * {@link TournamentMatch} row (load via {@code tournamentMatchRepository.findById},
     * rebuild with {@code buildingId} + a fresh {@code gameId}, save), builds
     * the enriched DTO with {@code buildingId} + {@code gameId} populated,
     * filters {@code activeLocalServers} to the single one whose
     * {@code buildingId} matches the assigned one, pushes via
     * {@code pushTournamentMatchToLocalServersPort.pushTournamentMatch(List.of(enrichedDto), targetServer)},
     * records {@code ReplicationProgress}, and markAsSent.
     *
     * <p>Structural mirror of {@link #replicateGameDefinitionEvent} but the
     * server filter narrows to the single building whose id == the assigned
     * {@code buildingId} (a tournament match is routed to exactly one
     * building, not all of them).</p>
     */
    private void replicateTournamentMatchEvent(OutboxEvent event,
                                               List<RegisteredLocalServer> activeLocalServers) {
        TournamentMatchScheduledDto dto;
        try {
            dto = objectMapper.readValue(event.getPayload(), TournamentMatchScheduledDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize tournament-match event [{}] due to malformed payload. Transitioning event to FAILED. Payload: {}",
                    event.getId(), event.getPayload(), e);
            try {
                outboxEventRepository.markAsFailed(event.getId());
            } catch (Exception dbEx) {
                log.error("Failed to mark event [{}] as FAILED in database", event.getId(), dbEx);
            }
            return;
        }

        // Q6: assign buildingId (round-robin) and gameId (fresh UUID) on the central TournamentMatch row.
        List<String> buildingIds =
                tournamentBuildingRepository.findByTournament(new TournamentId(dto.tournamentId()));
        if (buildingIds == null || buildingIds.isEmpty()) {
            log.warn("Tournament-match event [{}] — no buildings assigned to tournament {} — leaving event PENDING for a future tick.",
                    event.getId(), dto.tournamentId());
            return;
        }

        // Deterministic round-robin using the match's bracketPosition (1-based → 0-based).
        int matchIndex = Math.max(dto.bracketPosition() - 1, 0);
        String assignedBuildingId = buildingIds.get(matchIndex % buildingIds.size());
        String assignedGameId = UUID.randomUUID().toString();

        // Load the central TournamentMatch and patch it with buildingId + gameId.
        TournamentMatchId matchId = new TournamentMatchId(dto.matchId());
        Optional<TournamentMatch> matchOpt = tournamentMatchRepository.findById(matchId);
        if (matchOpt.isEmpty()) {
            log.warn("Tournament-match event [{}] — central TournamentMatch {} not found during drain — leaving event PENDING.",
                    event.getId(), dto.matchId());
            return;
        }
        TournamentMatch match = matchOpt.get();
        TournamentMatch patched = new TournamentMatch(
                match.getMatchId(), match.getTournamentId(),
                match.getRound(), match.getBracketPosition(),
                match.getParticipantA(), match.getParticipantB(),
                assignedBuildingId, assignedGameId, match.getSessionId(),
                match.getWinner(), match.getStatus(),
                match.getScheduledAt(), match.getPlayedAt(), match.getResultData());
        tournamentMatchRepository.save(patched);

        // Build the enriched DTO with buildingId + gameId populated.
        TournamentMatchScheduledDto enrichedDto = new TournamentMatchScheduledDto(
                dto.eventId(), dto.eventType(), dto.matchId(), dto.tournamentId(),
                dto.round(), dto.bracketPosition(),
                dto.participantA(), dto.participantB(),
                dto.gameType(), assignedGameId, dto.status(), dto.scheduledAt(),
                assignedBuildingId);

        // Filter activeLocalServers to the single one whose buildingId matches.
        List<RegisteredLocalServer> targetServers = activeLocalServers.stream()
                .filter(s -> assignedBuildingId.equals(s.getBuildingId().id()))
                .toList();
        if (targetServers.isEmpty()) {
            log.warn("Tournament-match event [{}] — no active local server with buildingId={} — leaving event PENDING for a future tick.",
                    event.getId(), assignedBuildingId);
            return;
        }
        RegisteredLocalServer targetServer = targetServers.get(0);
        String serverId = targetServer.getBuildingId().id();

        List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
        Set<String> alreadyReplicatedServerIds = progressList.stream()
                .map(ReplicationProgress::serverId)
                .collect(Collectors.toSet());

        AtomicBoolean allSucceeded = new AtomicBoolean(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (!alreadyReplicatedServerIds.contains(serverId)) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    pushTournamentMatchToLocalServersPort.pushTournamentMatch(List.of(enrichedDto), targetServer);
                } catch (Exception e) {
                    // No poison isolation for tournament-match — just log + flip allSucceeded
                    // so a future tick retries. The local upsert is idempotent by PK (matchId).
                    allSucceeded.set(false);
                    if (isConnectionRefused(e)) {
                        try {
                            localServerRegistryPort.deactivate(targetServer.getBuildingId());
                        } catch (Exception dex) {
                            log.warn("Failed to deactivate unreachable local server [{}]", targetServer.getBaseUrl(), dex);
                        }
                        log.warn("Local server [{}] unreachable (connection refused) — marked inactive; will re-activate on next heartbeat",
                                targetServer.getBaseUrl());
                    } else {
                        log.error("Failed to push tournament-match event [{}] to server [{}]: {}",
                                event.getId(), targetServer.getBaseUrl(), e.getMessage(), e);
                    }
                    return;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(event.getId(), serverId)) {
                    log.info("replication_progress already present (pre-check) for tournament-match eventId={}, serverId={} — treating as success",
                            event.getId(), serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                    } catch (DataIntegrityViolationException dup) {
                        log.info("replication_progress already present for tournament-match eventId={}, serverId={} — treating as success",
                                event.getId(), serverId);
                    }
                }
            }, replicationPushExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (allSucceeded.get()) {
            outboxEventRepository.markAsSent(event.getId());
        } else {
            log.warn("Tournament-match event [{}] was NOT marked as sent because the push failed.", event.getId());
        }
    }

    /**
     * True for any outbox event this scheduler should drain: user-replication
     * ({@code USER_REGISTERED}/{@code USER_UPDATED}) <em>or</em>
     * LOCAL_ADMIN↔building metadata events.
     */
    private boolean isReplicationEvent(OutboxEvent event) {
        return isUserReplicationEvent(event) || isMetadataEvent(event) || isGameDefinitionEvent(event)
                || isTournamentMatchEvent(event) || isTournamentSummaryEvent(event)
                || isTournamentStandingsEvent(event) || isTournamentParticipantsEvent(event)
                || isLocalServerRegistryEvent(event) || isTeamMembersEvent(event);
    }

    private boolean isUserReplicationEvent(OutboxEvent event) {
        return USER_REGISTERED_EVENT.equals(event.getEventType())
                || USER_UPDATED_EVENT.equals(event.getEventType());
    }

    private boolean isMetadataEvent(OutboxEvent event) {
        return LOCAL_ADMIN_BUILDING_ASSIGNED_EVENT.equals(event.getEventType())
                || LOCAL_ADMIN_BUILDING_REVOKED_EVENT.equals(event.getEventType());
    }

    private boolean isGameDefinitionEvent(OutboxEvent event) {
        return GAME_DEFINITION_UPSERTED_EVENT.equals(event.getEventType());
    }

    private boolean isTournamentMatchEvent(OutboxEvent event) {
        return TOURNAMENT_MATCH_SCHEDULED_EVENT.equals(event.getEventType());
    }

    private boolean isTournamentSummaryEvent(OutboxEvent event) {
        return TOURNAMENT_SUMMARY_UPSERTED_EVENT.equals(event.getEventType());
    }

    private boolean isTournamentStandingsEvent(OutboxEvent event) {
        return TOURNAMENT_STANDINGS_UPSERTED_EVENT.equals(event.getEventType());
    }

    private boolean isTournamentParticipantsEvent(OutboxEvent event) {
        return TOURNAMENT_PARTICIPANTS_UPSERTED_EVENT.equals(event.getEventType());
    }

    private boolean isLocalServerRegistryEvent(OutboxEvent event) {
        return LOCAL_SERVER_REGISTRY_UPSERTED_EVENT.equals(event.getEventType());
    }

    private boolean isTeamMembersEvent(OutboxEvent event) {
        return TEAM_MEMBERS_UPSERTED_EVENT.equals(event.getEventType());
    }

    private UserSyncDto deserializeUser(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), UserSyncDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize user replication event: " + event.getId(), e);
        }
    }

    private static boolean isConnectionRefused(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof ConnectException) {
                return true;
            }
            Throwable next = cur.getCause();
            if (next == cur) {
                break;
            }
            cur = next;
        }
        return false;
    }
}