package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ReplicationProgressJpaRepository;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closes the late-registration gap (FASE 4 step 3).
 *
 * <p>When a new local server registers AFTER user outbox events have already
 * been marked SENT (because the active-server list was empty when they were
 * processed), those events are never replicated to the new server by the
 * periodic scheduler — which only inspects PENDING events. This service replays
 * all SENT {@code USER_REGISTERED}/{@code USER_UPDATED} events that have not
 * yet been recorded in {@code replication_progress} for the newly-registered
 * server, pushing them in a single best-effort batch.</p>
 *
 * <p>Best-effort: if the push fails the error is logged and swallowed —
 * registration must still succeed. The local side deduplicates via the
 * upsert semantics of {@code PUT /internal/users/sync}.</p>
 */
@Service
public class LateRegistrationCatchUpService {

    private static final Logger log = LoggerFactory.getLogger(LateRegistrationCatchUpService.class);

    private static final String STATUS_SENT = "SENT";
    private static final List<String> USER_REPLICATION_EVENT_TYPES = List.of("USER_REGISTERED", "USER_UPDATED");

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final ObjectMapper objectMapper;
    private final ReplicationProgressJpaRepository replicationProgressJpaRepository;

    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                          PushUserToLocalServersPort pushUserToLocalServersPort,
                                          ObjectMapper objectMapper,
                                          ReplicationProgressJpaRepository replicationProgressJpaRepository) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.objectMapper = objectMapper;
        this.replicationProgressJpaRepository = replicationProgressJpaRepository;
    }

    /**
     * Run in a new tx so that the external PUT side-effect is decoupled from the
     * registration save tx.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void catchUpNewlyRegisteredServer(RegisteredLocalServer server) {
        String serverId = server.getBuildingId().id();

        List<OutboxEventJpaEntity> sentUserEvents =
                outboxEventJpaRepository.findByStatusAndEventTypeInOrderByCreatedAtAsc(
                        STATUS_SENT, USER_REPLICATION_EVENT_TYPES);

        if (sentUserEvents.isEmpty()) {
            log.info("Catch-up: no SENT user events to replicate to newly-registered server building={}", serverId);
            return;
        }

        List<String> eventIds = sentUserEvents.stream()
                .map(OutboxEventJpaEntity::getId)
                .toList();
        Set<String> alreadyReplicatedEventIds = replicationProgressJpaRepository
                .findByEventIdInAndServerId(eventIds, serverId).stream()
                .map(ReplicationProgressJpaEntity::getEventId)
                .collect(Collectors.toSet());

        List<UserSyncDto> usersToPush = new ArrayList<>();
        for (OutboxEventJpaEntity event : sentUserEvents) {
            if (alreadyReplicatedEventIds.contains(event.getId())) {
                continue;
            }
            try {
                UserSyncDto user = objectMapper.readValue(event.getPayload(), UserSyncDto.class);
                usersToPush.add(user);
            } catch (Exception e) {
                log.warn("Catch-up: skipping SENT event [{}] due to malformed payload: {}",
                        event.getId(), e.getMessage());
            }
        }

        if (usersToPush.isEmpty()) {
            log.info("Catch-up: nothing to push to newly-registered server building={} (all already replicated or malformed)",
                    serverId);
            return;
        }

        try {
            pushUserToLocalServersPort.pushUsers(usersToPush, server);
            log.info("Catch-up: pushed {} user records to newly-registered server building={}",
                    usersToPush.size(), serverId);
        } catch (Exception e) {
            // Best-effort: registration must still succeed even if catch-up fails.
            log.error("Catch-up: failed to push {} user records to newly-registered server building={}: {}. " +
                            "Local upsert dedup makes a later replay safe.",
                    usersToPush.size(), serverId, e.getMessage(), e);
        }
    }
}
