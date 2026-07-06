package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    /** Maximum number of pending events to fetch per scheduler run. */
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final LocalServerRegistryPort localServerRegistryPort;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final ReplicationProgressRepository replicationProgressRepository;
    private final ObjectMapper objectMapper;
    /**
     * Dedicated executor (bean {@code replicationPushExecutor}) used to push a
     * single user-replication event to all active local servers in parallel.
     * Kept separate from the {@code TaskScheduler} so blocking REST I/O never
     * starves {@code @Scheduled} methods.
     */
    private final Executor replicationPushExecutor;

    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper,
            @Qualifier("replicationPushExecutor") Executor replicationPushExecutor
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.localServerRegistryPort = localServerRegistryPort;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.replicationProgressRepository = replicationProgressRepository;
        this.objectMapper = objectMapper;
        this.replicationPushExecutor = replicationPushExecutor;
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
        // Fetch at most BATCH_SIZE events to avoid loading an unbounded result set
        List<OutboxEvent> pendingUserEvents = outboxEventRepository.findPendingLimit(BATCH_SIZE).stream()
                .filter(this::isUserReplicationEvent)
                .toList();

        if (pendingUserEvents.isEmpty()) {
            return;
        }

        List<RegisteredLocalServer> activeLocalServers = localServerRegistryPort.getActiveLocalServers();

        if (activeLocalServers.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingUserEvents) {
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
                        log.error("Failed to push user event [{}] to server [{}]: {}",
                                event.getId(), server.getBaseUrl(), e.getMessage(), e);
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

    private boolean isUserReplicationEvent(OutboxEvent event) {
        return USER_REGISTERED_EVENT.equals(event.getEventType())
                || USER_UPDATED_EVENT.equals(event.getEventType());
    }

    private UserSyncDto deserializeUser(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), UserSyncDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize user replication event: " + event.getId(), e);
        }
    }
}