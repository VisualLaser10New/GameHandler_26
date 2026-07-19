package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.model.TournamentMatch;
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
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
    private static final List<String> REPLAY_STATUSES = List.of(
            com.gameplatform.central.domain.model.OutboxEventStatus.SENT.name(),
            com.gameplatform.central.domain.model.OutboxEventStatus.PENDING.name());
    /** D2: replay user-replication AND LOCAL_ADMIN↔building metadata events. */
    private static final List<String> REPLICATION_EVENT_TYPES = List.of(
            "USER_REGISTERED", "USER_UPDATED",
            "LOCAL_ADMIN_BUILDING_ASSIGNED", "LOCAL_ADMIN_BUILDING_REVOKED",
            "GAME_DEFINITION_UPSERTED",
            "TOURNAMENT_MATCH_SCHEDULED",
            "TOURNAMENT_SUMMARY_UPSERTED",
            "TOURNAMENT_STANDINGS_UPSERTED",
            "TOURNAMENT_PARTICIPANTS_UPSERTED",
            "LOCAL_SERVER_REGISTRY_UPSERTED",
            "TEAM_MEMBERS_UPSERTED");

private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final PushMetadataToLocalServersPort pushMetadataToLocalServersPort;
    private final PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort;
    private final ObjectMapper objectMapper;
    private final ReplicationProgressRepository replicationProgressRepository;
    private final PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort;
    private final PushTournamentStandingsToLocalServersPort pushTournamentStandingsToLocalServersPort;
    private final PushTournamentParticipantsToLocalServersPort pushTournamentParticipantsToLocalServersPort;
    private final PushTeamMembersToLocalServersPort pushTeamMembersToLocalServersPort;
    private final PushLocalServerRegistryToLocalServersPort pushLocalServerRegistryToLocalServersPort;

    @org.springframework.beans.factory.annotation.Autowired
    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       PushUserToLocalServersPort pushUserToLocalServersPort,
                                       ObjectMapper objectMapper,
                                       ReplicationProgressRepository replicationProgressRepository,
                                       PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
                                       PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
                                       PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
                                       TournamentMatchRepository tournamentMatchRepository,
                                       PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort,
                                       PushTournamentStandingsToLocalServersPort pushTournamentStandingsToLocalServersPort,
                                       PushTournamentParticipantsToLocalServersPort pushTournamentParticipantsToLocalServersPort,
                                       PushTeamMembersToLocalServersPort pushTeamMembersToLocalServersPort,
                                       PushLocalServerRegistryToLocalServersPort pushLocalServerRegistryToLocalServersPort) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.objectMapper = objectMapper;
        this.replicationProgressRepository = replicationProgressRepository;
        this.pushMetadataToLocalServersPort = pushMetadataToLocalServersPort;
        this.pushGameDefinitionToLocalServersPort = pushGameDefinitionToLocalServersPort;
        this.pushTournamentMatchToLocalServersPort = pushTournamentMatchToLocalServersPort;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.pushTournamentSummaryToLocalServersPort = pushTournamentSummaryToLocalServersPort;
        this.pushTournamentStandingsToLocalServersPort = pushTournamentStandingsToLocalServersPort;
        this.pushTournamentParticipantsToLocalServersPort = pushTournamentParticipantsToLocalServersPort;
        this.pushTeamMembersToLocalServersPort = pushTeamMembersToLocalServersPort;
        this.pushLocalServerRegistryToLocalServersPort = pushLocalServerRegistryToLocalServersPort;
    }

    /**
     * Backward-compat legacy ctor (S2): 10-arg delegating to the 14-arg production
     * ctor with {@code null} for the four new S3/BUG-TEAM-3 ports. Transitive delegation
     * reaches the 14-arg production ctor.
     */
    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       PushUserToLocalServersPort pushUserToLocalServersPort,
                                       ObjectMapper objectMapper,
                                       ReplicationProgressRepository replicationProgressRepository,
                                       PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
                                       PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
                                       PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
                                       TournamentMatchRepository tournamentMatchRepository,
                                       PushTournamentSummaryToLocalServersPort pushTournamentSummaryToLocalServersPort) {
        this(outboxEventJpaRepository, outboxEventRepository, pushUserToLocalServersPort,
                objectMapper, replicationProgressRepository, pushMetadataToLocalServersPort,
                pushGameDefinitionToLocalServersPort, pushTournamentMatchToLocalServersPort,
                tournamentMatchRepository, pushTournamentSummaryToLocalServersPort,
                null, null, null, null);
    }

    /** Backward-compat legacy ctor (pre-S2): 9-arg delegating to the 10-arg with {@code null} for the new port. */
    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       PushUserToLocalServersPort pushUserToLocalServersPort,
                                       ObjectMapper objectMapper,
                                       ReplicationProgressRepository replicationProgressRepository,
                                       PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
                                       PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
                                       PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort,
                                       TournamentMatchRepository tournamentMatchRepository) {
        this(outboxEventJpaRepository, outboxEventRepository, pushUserToLocalServersPort,
                objectMapper, replicationProgressRepository, pushMetadataToLocalServersPort,
                pushGameDefinitionToLocalServersPort, pushTournamentMatchToLocalServersPort,
                tournamentMatchRepository, null);
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
    /**
     * Replica al server locale appena registrato tutti gli eventi di replica
     * (stato {@code SENT} e {@code PENDING}) non ancora presenti nella sua
     * {@code replication_progress}.
     *
     * <p>Chiude il gap di replicazione per i server che si registrano dopo che
     * gli eventi utente sono già stati processati e mai inviati loro dal
     * scheduler periodico. Ogni evento è propagato singolarmente per isolare
     * eventuali eventi "avvelenati" e il relativo progresso è registrato per
     * evitare repliche duplicate. In caso di errore di push il fallimento è
     * registrato e ignorato (best-effort) così che la registrazione abbia
     * comunque successo.</p>
     *
     * @param server il server locale appena registrato (non deve essere {@code null})
     * @see #isMetadataEvent(String)
     * @see #isGameDefinitionEvent(String)
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

            // FASE 7-A2 — tournament-summary event branch: TOURNAMENT_SUMMARY_UPSERTED.
            // No ack / poison isolation: the local upsert is idempotent by PK
            // (tournamentId). The summary projection is global (no per-building
            // routing filter, every active server replicates it) and a
            // {@code deleted=true} tombstone is handled by the local side as a
            // {@code deleteById} on the projection. Best-effort push (failure
            // swallowed) preserves the rest of the catch-up batch.
            if (isTournamentSummaryEvent(eventType)) {
                if (pushTournamentSummaryToLocalServersPort == null) {
                    log.warn("Catch-up: tournament-summary event [{}] — no PushTournamentSummaryToLocalServersPort wired (legacy ctor) — skipping.",
                            eventId);
                    continue;
                }
                TournamentSummaryEventDto summaryEvent;
                try {
                    summaryEvent = objectMapper.readValue(event.getPayload(), TournamentSummaryEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping tournament-summary event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushTournamentSummaryToLocalServersPort.push(List.of(summaryEvent), server);
                } catch (Exception e) {
                    log.error("Catch-up: failed to push tournament-summary event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for tournament-summary eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for tournament-summary eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // FASE 7-A3 — tournament-standings event branch: TOURNAMENT_STANDINGS_UPSERTED.
            // No ack / poison isolation: the local upsert is a delete+insert snapshot
            // idempotent by PK (tournamentId). Broadcast (no per-building routing).
            if (isTournamentStandingsEvent(eventType)) {
                if (pushTournamentStandingsToLocalServersPort == null) {
                    log.warn("Catch-up: tournament-standings event [{}] — no PushTournamentStandingsToLocalServersPort wired (legacy ctor) — skipping.",
                            eventId);
                    continue;
                }
                TournamentStandingsEventDto standingsEvent;
                try {
                    standingsEvent = objectMapper.readValue(event.getPayload(), TournamentStandingsEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping tournament-standings event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushTournamentStandingsToLocalServersPort.push(List.of(standingsEvent), server);
                } catch (Exception e) {
                    log.error("Catch-up: failed to push tournament-standings event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for tournament-standings eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for tournament-standings eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // FASE 7-A3 — tournament-participants event branch: TOURNAMENT_PARTICIPANTS_UPSERTED.
            // No ack / poison isolation: the local upsert is a delete+insert snapshot
            // idempotent by PK (tournamentId). Broadcast (no per-building routing).
            if (isTournamentParticipantsEvent(eventType)) {
                if (pushTournamentParticipantsToLocalServersPort == null) {
                    log.warn("Catch-up: tournament-participants event [{}] — no PushTournamentParticipantsToLocalServersPort wired (legacy ctor) — skipping.",
                            eventId);
                    continue;
                }
                TournamentParticipantsEventDto participantsEvent;
                try {
                    participantsEvent = objectMapper.readValue(event.getPayload(), TournamentParticipantsEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping tournament-participants event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushTournamentParticipantsToLocalServersPort.push(List.of(participantsEvent), server);
                } catch (Exception e) {
                    log.error("Catch-up: failed to push tournament-participants event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for tournament-participants eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for tournament-participants eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // FASE 7-A3 — local-server-registry event branch: LOCAL_SERVER_REGISTRY_UPSERTED.
            // No ack / poison isolation: the local upsert is idempotent by PK (buildingId).
            // Broadcast (no per-building routing) so every Local mirrors the full registry.
            if (isLocalServerRegistryEvent(eventType)) {
                if (pushLocalServerRegistryToLocalServersPort == null) {
                    log.warn("Catch-up: local-server-registry event [{}] — no PushLocalServerRegistryToLocalServersPort wired (legacy ctor) — skipping.",
                            eventId);
                    continue;
                }
                LocalServerRegistryEventDto registryEvent;
                try {
                    registryEvent = objectMapper.readValue(event.getPayload(), LocalServerRegistryEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping local-server-registry event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushLocalServerRegistryToLocalServersPort.push(List.of(registryEvent), server);
                } catch (Exception e) {
                    log.error("Catch-up: failed to push local-server-registry event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for local-server-registry eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for local-server-registry eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // BUG-TEAM-3 — team-members event branch: TEAM_MEMBERS_UPSERTED.
            // No ack / poison isolation: the local upsert is a delete+insert snapshot
            // idempotent by PK (tournamentId). Broadcast (no per-building routing) so
            // every Local hosting any match of the tournament can resolve team→user
            // membership for the myMatches JPQL join.
            if (isTeamMembersEvent(eventType)) {
                if (pushTeamMembersToLocalServersPort == null) {
                    log.warn("Catch-up: team-members event [{}] — no PushTeamMembersToLocalServersPort wired (legacy ctor) — skipping.",
                            eventId);
                    continue;
                }
                TeamMembersEventDto teamMembersEvent;
                try {
                    teamMembersEvent = objectMapper.readValue(event.getPayload(), TeamMembersEventDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping team-members event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                try {
                    pushTeamMembersToLocalServersPort.push(List.of(teamMembersEvent), server);
                } catch (Exception e) {
                    log.error("Catch-up: failed to push team-members event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for team-members eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for team-members eventId={}, building={} — treating as success",
                                eventId, serverId);
                    }
                }
                continue;
            }

            // FASE 6 — tournament-match scheduled event branch.
            // Catch-up: the building is ALREADY assigned (the dto carries
            // buildingId OR the central match row does). Push ONLY if the
            // server's buildingId matches the resolved buildingId.
            // No ack / poison isolation: the local upsert is idempotent by PK
            // (matchId), so a best-effort push (failure swallowed) is enough.
            if (isTournamentMatchEvent(eventType)) {
                TournamentMatchScheduledDto dto;
                try {
                    dto = objectMapper.readValue(event.getPayload(), TournamentMatchScheduledDto.class);
                } catch (Exception e) {
                    log.warn("Catch-up: skipping tournament-match event [{}] due to malformed payload: {}", eventId, e.getMessage());
                    continue;
                }

                // Resolve buildingId (dto.buildingId() may be null if the event was
                // replayed before the drain branch assigned it — load the central
                // match row then).
                String matchBuildingId = dto.buildingId();
                if (matchBuildingId == null) {
                    Optional<TournamentMatch> matchOpt =
                            tournamentMatchRepository.findById(new TournamentMatchId(dto.matchId()));
                    if (matchOpt.isPresent()) {
                        matchBuildingId = matchOpt.get().getBuildingId();
                    }
                }
                if (matchBuildingId == null || !matchBuildingId.equals(serverId)) {
                    // Either no building assigned yet, or this match was for a different building — skip.
                    log.info("Catch-up: tournament-match event [{}] not for building={} (resolved buildingId={}) — skipping",
                            eventId, serverId, matchBuildingId);
                    continue;
                }

                try {
                    pushTournamentMatchToLocalServersPort.pushTournamentMatch(List.of(dto), server);
                } catch (Exception e) {
                    // Best-effort: a failing push for one event must NOT abort the rest.
                    log.error("Catch-up: failed to push tournament-match event [{}] (type={}) to building={}: {}",
                            eventId, eventType, serverId, e.getMessage(), e);
                    continue;
                }

                if (replicationProgressRepository.existsByEventIdAndServerId(eventId, serverId)) {
                    log.info("Catch-up: replication_progress already present (pre-check) for tournament-match eventId={}, building={}", eventId, serverId);
                } else {
                    try {
                        replicationProgressRepository.save(new ReplicationProgress(eventId, serverId));
                        pushed++;
                    } catch (DataIntegrityViolationException dup) {
                        log.info("Catch-up: replication_progress already present for tournament-match eventId={}, building={} — treating as success",
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

    /**
     * Verifica se il tipo di evento indicato appartiene agli eventi di
     * metadati di binding LOCAL_ADMIN↔building.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code LOCAL_ADMIN_BUILDING_ASSIGNED}
     *         o {@code LOCAL_ADMIN_BUILDING_REVOKED}, {@code false} altrimenti
     */
    private static boolean isMetadataEvent(String eventType) {
        return "LOCAL_ADMIN_BUILDING_ASSIGNED".equals(eventType)
                || "LOCAL_ADMIN_BUILDING_REVOKED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * upsert di una definizione di gioco.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code GAME_DEFINITION_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isGameDefinitionEvent(String eventType) {
        return "GAME_DEFINITION_UPSERTED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * torneo pianificato.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code TOURNAMENT_MATCH_SCHEDULED},
     *         {@code false} altrimenti
     */
    private static boolean isTournamentMatchEvent(String eventType) {
        return "TOURNAMENT_MATCH_SCHEDULED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * riepilogo di torneo.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code TOURNAMENT_SUMMARY_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isTournamentSummaryEvent(String eventType) {
        return "TOURNAMENT_SUMMARY_UPSERTED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * classifica di torneo.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code TOURNAMENT_STANDINGS_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isTournamentStandingsEvent(String eventType) {
        return "TOURNAMENT_STANDINGS_UPSERTED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * partecipanti di torneo.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code TOURNAMENT_PARTICIPANTS_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isTournamentParticipantsEvent(String eventType) {
        return "TOURNAMENT_PARTICIPANTS_UPSERTED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * registro dei Local Server.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code LOCAL_SERVER_REGISTRY_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isLocalServerRegistryEvent(String eventType) {
        return "LOCAL_SERVER_REGISTRY_UPSERTED".equals(eventType);
    }

    /**
     * Verifica se il tipo di evento indicato corrisponde a un evento di
     * membri di squadra di torneo.
     *
     * @param eventType il tipo di evento da valutare (può essere {@code null})
     * @return {@code true} se l'evento è {@code TEAM_MEMBERS_UPSERTED},
     *         {@code false} altrimenti
     */
    private static boolean isTeamMembersEvent(String eventType) {
        return "TEAM_MEMBERS_UPSERTED".equals(eventType);
    }
}
