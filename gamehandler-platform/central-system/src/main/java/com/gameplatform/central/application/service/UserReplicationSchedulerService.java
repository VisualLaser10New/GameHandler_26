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
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
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

    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.localServerRegistryPort = localServerRegistryPort;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.replicationProgressRepository = replicationProgressRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls for pending user-replication events and pushes them to local servers.
     *
     * <p>{@code fixedDelay} ensures no overlapping runs – the 5-minute window begins
     * only after the previous invocation finishes.</p>
     */
    @Scheduled(fixedDelay = 300_000)
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
            UserSyncDto user = deserializeUser(event);

            List<ReplicationProgress> progressList = replicationProgressRepository.findByEventId(event.getId());
            Set<String> alreadyReplicatedServerIds = progressList.stream()
                    .map(ReplicationProgress::serverId)
                    .collect(Collectors.toSet());

            // Track whether all servers received the event successfully
            boolean allSucceeded = true;

            for (RegisteredLocalServer server : activeLocalServers) {
                String serverId = server.getBuildingId().id();
                if (alreadyReplicatedServerIds.contains(serverId)) {
                    continue;
                }
                try {
                    pushUserToLocalServersPort.pushUsers(List.of(user), server);
                    replicationProgressRepository.save(new ReplicationProgress(event.getId(), serverId));
                } catch (Exception e) {
                    // Isolate per-server failures: log and continue to the next server
                    allSucceeded = false;
                    log.error("Failed to push user event [{}] to server [{}]: {}",
                            event.getId(), server.getBaseUrl(), e.getMessage(), e);
                }
            }

            // Only mark as sent when the event reached every active server
            if (allSucceeded) {
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