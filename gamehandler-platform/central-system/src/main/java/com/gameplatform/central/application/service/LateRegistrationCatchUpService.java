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
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Closes the late-registration gap (FASE 4 step 3 / R1).
 *
 * <p>When a new local server registers AFTER user outbox events have already
 * been processed (because the active-server list was empty when they were
 * processed), those events are never replicated to the new server by the
 * periodic scheduler — which only inspects PENDING events. This service replays
 * all SENT <em>and</em> PENDING {@code USER_REGISTERED}/{@code USER_UPDATED}
 * events that have not yet been recorded in {@code replication_progress} for the
 * newly-registered server, pushing them one event at a time so a poison user
 * never aborts the rest of the batch.</p>
 *
 * <p>R1: replays {@code SENT} + {@code PENDING} ({@link #REPLAY_STATUSES});
 * per-event push ({@code List.of(user)}) for poison isolation; records
 * {@code replication_progress} per-event via the domain
 * {@link ReplicationProgressRepository} port, swallowing
 * {@link DataIntegrityViolationException} on duplicate (event_id, server_id)
 * inserts; consumes the M3 {@link UserSyncAckDto} contract —
 * {@code applied=true} or {@code STALE_EVENT} → record progress;
 * {@code VALIDATION_ERROR} → mark the event FAILED and skip the progress write.</p>
 *
 * <p>Best-effort: if a push fails the error is logged and swallowed —
 * registration must still succeed. The local side deduplicates via the
 * upsert semantics of {@code PUT /internal/users/sync}.</p>
 */
@Service
public class LateRegistrationCatchUpService {

    private static final Logger log = LoggerFactory.getLogger(LateRegistrationCatchUpService.class);

    /** R1: replay both SENT (already broadcast to old servers) and PENDING (never sent). */
    private static final List<String> REPLAY_STATUSES = List.of("SENT", "PENDING");
    /** D2: replay user-replication AND LOCAL_ADMIN↔building metadata events. */
    private static final List<String> REPLICATION_EVENT_TYPES = List.of(
            "USER_REGISTERED", "USER_UPDATED",
            "LOCAL_ADMIN_BUILDING_ASSIGNED", "LOCAL_ADMIN_BUILDING_REVOKED",
            "GAME_DEFINITION_UPSERTED");

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    private final PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    private final ObjectMapper objectMapper;
    private final ReplicationProgressRepository replicationProgressRepository;

    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                      OutboxEventRepository outboxEventRepository,
                                      PushUserToLocalServersPort pushUserToLocalServersPort,
                                      ObjectMapper objectMapper,
                                      ReplicationProgressRepository replicationProgressRepository,
                                      PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
                                      PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.objectMapper = objectMapper;
        this.replicationProgressRepository = replicationProgressRepository;
        this.pushMetadataToLocalServersPort = pushMetadataToLocalServersPort;
        this.pushGameDefinitionToLocalServersPort = pushGameDefinitionToLocalServersPort;
    }

    /**
     * Intentionally <b>NOT</b> transactional.
     *
     * <p>This method iterates SENT/PENDING user outbox events and, per event,
     * performs an external REST push ({@code pushUserToLocalServersPort.pushUsers})
     * followed by a {@code replication_progress} write. Running the whole loop
     * inside one tx would be the poison-isolation / long-tx anti-pattern
     * (BUG-SYNC-01 / C-01) flagged by
     * {@code ArchitectureInvariantTest.noTransactionalMethodWithLoopCallingOutboundPort}:
     * a long tx holding locks while iterating external calls lets a single
     * failure poison the entire batch.</p>
     *
     * <p>Instead, each per-event step commits independently:
     * <ul>
     *   <li>{@code replicationProgressRepository.save(...)} commits via the
     *       adapter / Spring Data {@code SimpleJpaRepository.save}'s own
     *       {@code @Transactional};</li>
     *   <li>{@code outboxEventRepository.markAsFailed(eventId)} commits via the
     *       adapter's own {@code @Transactional}.</li>
     * </ul>
     * So a duplicate (DIVE) or a failure on one event does NOT roll back
     * progress already recorded for other events — partial catch-up progress
     * is preserved. The {@code pushUsers} REST call needs no tx.</p>
     *
     * <p>M8 invokes this from {@code TransactionSynchronization.afterCommit}
     * inside {@code LocalServerRepositoryAdapter.register}, so by the time it
     * runs the registration has already committed and there is no surrounding
     * registration tx to either join or conflict with — hence no
     * {@code @Transactional(propagation = REQUIRES_NEW)} is required here.</p>
     */
    public void catchUpNewlyRegisteredServer(RegisteredLocalServer server) {
        String serverId = server.getBuildingId().id();

        List<OutboxEventJpaEntity> replayableEvents =
                outboxEventJpaRepository.findByStatusInAndEventTypeInOrderByCreatedAtAsc(
                        REPLAY_STATUSES, REPLICATION_EVENT_TYPES);

        if (replayableEvents.isEmpty()) {
            log.info("Catch-up: no SENT/PENDING replication events to replicate to newly-registered server building={}", serverId);
            return;
        }

        int pushed = 0;
        int skipped = 0;
        for (OutboxEventJpaEntity event : replayableEvents) {
            String eventId = event.getId();
            String eventType = event.getEventType();

            if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                log.info("Catch-up: eventId={} already replicated to building={} — skipping push", eventId, serverId);
                skipped++;
                continue;
            }

            // D2 — metadata event branch: LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED.
            // No ack / poison isolation: the local upsert/delete is idempotent by
            // composite PK, so a best-effort push (failure swallowed) is enough.
            if (isMetadataEvent(eventType)) {
                LocalAdminBuildingEventDto metadataEvent;
                try {
                    metadataEvent = objectMapper.readValue(event.getPayload(), LocalAdminBuildingEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping metadata event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushMetadataToLocalServersPort.pushMetadata(List.of(metadataEvent), server);
                } catch (Exception e) {
                    // Best-effort: a failing push for one event must NOT abort the rest.
                    log.error("Catch-up: failed to push metadata event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for metadata eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for metadata eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // FASE 2 — game-definition event branch: GAME_DEFINITION_UPSERTED.
            // No ack / poison isolation: the local upsert is idempotent by PK
            // (game_type), so a best-effort push (failure swallowed) is enough.
            if (isGameDefinitionEvent(eventType)) {
                GameDefinitionEventDto gameDefinitionEvent;
                try {
                    gameDefinitionEvent = objectMapper.readValue(event.getPayload(), GameDefinitionEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping game-definition event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushGameDefinitionToLocalServersPort.pushGameDefinitions(List.of(gameDefinitionEvent), server);
                } catch (Exception e) {
                    // Best-effort: a failing push for one event must NOT abort the rest.
                    log.error("Catch-up: failed to push game-definition event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for game-definition eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for game-definition eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            UserSyncDto user;
            try {
                user = objectMapper.readValue(event.getPayload(), UserSyncDto.class);
            } catch (Exception e) {
                log.warn("Catch-up: skipping event [{}] due to malformed payload: {}", eventId, e.getMessage());
                continue;
            }

            List<UserSyncAckDto> acks;
            try {
                acks = pushUserToLocalServersPort.pushUsers(List.of(user), server);
            } catch (Exception e) {
                // Best-effort: a failing push for one event must NOT abort the rest.
                log.error("Catch-up: failed to push event [{}] (type={}) to building={}: {}",
                        eventId, eventType, serverId, e.getMessage(), e);
                continue;
            }

            UserSyncAckDto ack = (acks == null || acks.isEmpty()) ? null : acks.get(0);

            if (ack != null && !ack.applied() && ack.reason() != null && ack.reason().startsWith("VALIDATION_ERROR")) {
                // Poison user: quarantine this event, do NOT record progress, continue.
                log.warn("Catch-up: poison user isolation for eventId={} building={} type={} reason={}",
                        eventId, serverId, eventType, ack.reason());
                try {
                    outboxEventRepository.markAsFailed(eventId);
                } catch (Exception dbEx) {
                    log.error("Catch-up: failed to mark event [{}] as FAILED", eventId, dbEx);
                }
                continue;
            }

            // applied=true OR STALE_EVENT OR (no ack body → legacy success) → record progress.
            if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                log.info("Catch-up: replication_progress already present (pre-check) for eventId={}, building={}", eventId, serverId);
            } else {
                try {
                    replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                    pushed++;
                } catch (DataIntegrityViolationException dup) {
                    log.info("Catch-up: replication_progress already present for eventId={}, building={} — treating as success",
                            eventId, serverId);
                }
            }
        }

        if (pushed == 0 && skipped == 0) {
            log.info("Catch-up: nothing pushed to newly-registered server building={} (all already replicated, malformed, or poison)",
                    serverId);
        } else {
            log.info("Catch-up: pushed {} records to newly-registered server building={} ({} already replicated)",
                    pushed, serverId, skipped);
        }
    }

    private static boolean isMetadataEvent(String eventType) {
        return "LOCAL_ADMIN_BUILDING_ASSIGNED".equals(eventType)
                || "LOCAL_ADMIN_BUILDING_REVOKED".equals(eventType);
    }

    private static boolean isGameDefinitionEvent(String eventType) {
        return "GAME_DEFINITION_UPSERTED".equals(eventType);
    }
}
