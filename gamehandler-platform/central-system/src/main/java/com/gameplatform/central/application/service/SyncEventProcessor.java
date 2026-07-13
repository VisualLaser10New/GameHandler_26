package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.FirstBucketRaceHandledException;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.DeleteTournamentUseCase;
import com.gameplatform.central.domain.ports.in.EmitTournamentSummaryUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.ScheduleTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.UpdateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.UpdateUserUseCase;
import com.gameplatform.central.domain.ports.in.UpsertGameDefinitionUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.GameDefinitionUpsertRequestedEventDto;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.ParticipantRegisterRequestedEventDto;
import com.gameplatform.shared.dto.RoleAssignmentRequestedEventDto;
import com.gameplatform.shared.dto.TournamentCreateRequestedEventDto;
import com.gameplatform.shared.dto.TournamentDeleteRequestedEventDto;
import com.gameplatform.shared.dto.TournamentLifecycleRequestedEventDto;
import com.gameplatform.shared.dto.TournamentMatchResultDto;
import com.gameplatform.shared.dto.TournamentUpdateRequestedEventDto;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-event processor for sync events. Each event is processed in its own
 * REQUIRES_NEW transaction so a poison event only aborts its own tx, not the
 * whole batch (poison isolation, fix for BUG-SYNC-01 / C-01).
 *
 * <p>Returns {@code true} on success (event processed + marked in processed_events
 * in its own tx); {@code false} if the event was a duplicate (already in
 * processed_events OR caught by DB unique constraint). Throws on real failures
 * so the caller can decide to mark the event as processed (poison isolation).</p>
 */
@Service
public class SyncEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(SyncEventProcessor.class);

    private final ProcessedEventRepository processedEventRepository;
    private final StatisticsRepository statisticsRepository;
    private final RegisterUserFromSyncUseCase registerUserFromSyncUseCase;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final StatisticsFirstBucketRaceRetryHelper retryHelper;

    /**
     * FASE 3 player read-model projection. May be {@code null} (the backward-compat
     * constructors used by existing unit tests pass {@code null}); when it is, the
     * {@code GAME_SESSION_COMPLETED} branch skips the player read-model update,
     * keeping the historical (FASE 0/1/2) behaviour byte-identical. In production
     * Spring injects a real {@link PlayerStatisticsProjectionService} via the
     * {@code @Autowired} constructor below.
     */
    private final PlayerStatisticsProjectionService playerStatisticsProjection;

    /**
     * FASE 6 tournament-match completion ports/services. May be {@code null}
     * (the backward-compat constructors used by existing unit tests pass
     * {@code null} for these via the 7-arg delegating ctor); when {@code null},
     * the {@code TOURNAMENT_MATCH_COMPLETED} branch logs a warning and skips
     * (keeping the historical behaviour for legacy tests). In production
     * Spring injects real beans via the {@code @Autowired} 11-arg constructor
     * below.
     */
    private final TournamentBracketService tournamentBracketService;
    private final TournamentStandingsService tournamentStandingsService;
    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository tournamentMatchRepository;

    /**
     * FASE 7-A3/S3 admin-request use cases. May be {@code null} (the
     * backward-compat constructors used by existing unit tests pass
     * {@code null} for these via the 11-arg delegating ctor); when {@code null},
     * the matching {@code *_REQUESTED} branch logs a warning and skips
     * (keeping the historical behaviour for legacy tests). In production Spring
     * injects real beans via the {@code @Autowired} 21-arg constructor below.
     */
    private final UpdateUserUseCase updateUserUseCase;
    private final UpsertGameDefinitionUseCase upsertGameDefinitionUseCase;
    private final CreateTournamentUseCase createTournamentUseCase;
    private final OpenTournamentRegistrationUseCase openTournamentRegistrationUseCase;
    private final CancelTournamentUseCase cancelTournamentUseCase;
    private final ScheduleTournamentMatchesUseCase scheduleTournamentMatchesUseCase;
    private final UpdateTournamentUseCase updateTournamentUseCase;
    private final DeleteTournamentUseCase deleteTournamentUseCase;
    private final RegisterTournamentParticipantUseCase registerTournamentParticipantUseCase;

    /**
     * BUG-SCHEDULE-REQUEST-ID: emits a {@code TOURNAMENT_SUMMARY_UPSERTED}
     * return event carrying {@code dto.requestId()} as
     * {@code originatingRequestId} after a SCHEDULE, so the Local
     * {@code admin_requests_local} row transitions to COMPLETED. May be
     * {@code null} (the backward-compat 11-arg delegating ctor used by legacy
     * unit tests passes {@code null}); when {@code null}, the
     * {@code TOURNAMENT_SCHEDULE_REQUESTED} branch logs a warning and skips
     * the summary emit (keeping the historical behaviour for legacy tests).
     */
    private final EmitTournamentSummaryUseCase emitTournamentSummaryUseCase;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                              StatisticsRepository statisticsRepository,
                              RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                              ObjectMapper objectMapper,
                              Clock clock,
                              StatisticsFirstBucketRaceRetryHelper retryHelper,
                              PlayerStatisticsProjectionService playerStatisticsProjection,
                              TournamentBracketService tournamentBracketService,
                              TournamentStandingsService tournamentStandingsService,
                              TournamentRepository tournamentRepository,
                              TournamentMatchRepository tournamentMatchRepository,
                              UpdateUserUseCase updateUserUseCase,
                              UpsertGameDefinitionUseCase upsertGameDefinitionUseCase,
                              CreateTournamentUseCase createTournamentUseCase,
                              OpenTournamentRegistrationUseCase openTournamentRegistrationUseCase,
                              CancelTournamentUseCase cancelTournamentUseCase,
                              ScheduleTournamentMatchesUseCase scheduleTournamentMatchesUseCase,
                              UpdateTournamentUseCase updateTournamentUseCase,
                              DeleteTournamentUseCase deleteTournamentUseCase,
                              RegisterTournamentParticipantUseCase registerTournamentParticipantUseCase,
                              EmitTournamentSummaryUseCase emitTournamentSummaryUseCase) {
        this.processedEventRepository = processedEventRepository;
        this.statisticsRepository = statisticsRepository;
        this.registerUserFromSyncUseCase = registerUserFromSyncUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retryHelper = retryHelper;
        this.playerStatisticsProjection = playerStatisticsProjection;
        this.tournamentBracketService = tournamentBracketService;
        this.tournamentStandingsService = tournamentStandingsService;
        this.tournamentRepository = tournamentRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.updateUserUseCase = updateUserUseCase;
        this.upsertGameDefinitionUseCase = upsertGameDefinitionUseCase;
        this.createTournamentUseCase = createTournamentUseCase;
        this.openTournamentRegistrationUseCase = openTournamentRegistrationUseCase;
        this.cancelTournamentUseCase = cancelTournamentUseCase;
        this.scheduleTournamentMatchesUseCase = scheduleTournamentMatchesUseCase;
        this.updateTournamentUseCase = updateTournamentUseCase;
        this.deleteTournamentUseCase = deleteTournamentUseCase;
        this.registerTournamentParticipantUseCase = registerTournamentParticipantUseCase;
        this.emitTournamentSummaryUseCase = emitTournamentSummaryUseCase;
    }

    /**
     * Backward-compat legacy ctor (FASE 6): 11-arg delegating to the 21-arg
     * production ctor with {@code null} for the nine FASE 7-A3/S3 admin-request
     * use cases and the BUG-SCHEDULE-REQUEST-ID summary-emit use case. Preserves
     * existing FASE 6 unit tests that still use the 11-arg ctor without stubs
     * for the new use cases; the {@code @Autowired} 21-arg ctor remains the
     * production entry point. When any of the ten is {@code null}, the matching
     * {@code *_REQUESTED} branch logs a warning and skips.
     */
    public SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                              StatisticsRepository statisticsRepository,
                              RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                              ObjectMapper objectMapper,
                              Clock clock,
                              StatisticsFirstBucketRaceRetryHelper retryHelper,
                              PlayerStatisticsProjectionService playerStatisticsProjection,
                              TournamentBracketService tournamentBracketService,
                              TournamentStandingsService tournamentStandingsService,
                              TournamentRepository tournamentRepository,
                              TournamentMatchRepository tournamentMatchRepository) {
        this(processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock, retryHelper,
                playerStatisticsProjection, tournamentBracketService, tournamentStandingsService,
                tournamentRepository, tournamentMatchRepository,
                null, null, null, null, null, null, null, null, null, null);
    }

    SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                       StatisticsRepository statisticsRepository,
                       RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                       ObjectMapper objectMapper,
                       Clock clock,
                       StatisticsFirstBucketRaceRetryHelper retryHelper,
                       PlayerStatisticsProjectionService playerStatisticsProjection) {
        this(processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock, retryHelper,
                playerStatisticsProjection, null, null, null, null);
    }

    SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                       StatisticsRepository statisticsRepository,
                       RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                       ObjectMapper objectMapper,
                       Clock clock,
                       StatisticsFirstBucketRaceRetryHelper retryHelper) {
        this(processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock, retryHelper, null);
    }

    SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                       StatisticsRepository statisticsRepository,
                       RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this(processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock,
                new StatisticsFirstBucketRaceRetryHelper(statisticsRepository, processedEventRepository, clock));
    }

    /**
     * Process a single event in a new tx.
     *
     * @return true if processed and marked; false if duplicate.
     * @throws Exception on real processing failure (caller will mark processed to isolate).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(BuildingId buildingId, OutboxEventDto event) throws Exception {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate sync event caught, skipping: {}", event.eventId());
            return false;
        }
        boolean processed;
        try {
            processed = processEvent(buildingId, event);
        } catch (FirstBucketRaceHandledException race) {
            // First-bucket insert race resolved in a fresh REQUIRES_NEW tx by the retry
            // helper, which already committed BOTH the merged stats AND the processed_events
            // record. Skip the normal processed_events save and return success.
            return true;
        } catch (DataIntegrityViolationException dup) {
            // race-condition duplicate of processed_events PK
            log.info("Duplicate sync event caught by DB constraint, skipping: {}", event.eventId());
            return false;
        } catch (JsonProcessingException e) {
            log.warn("Sync event [{}] payload malformed: {}", event.eventId(), e.getMessage());
            markProcessed(event.eventId());
            return false;
        }
        try {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate sync event caught by DB constraint, skipping: {}", event.eventId());
            return false;
        }
        return processed;
    }

    /**
     * Marks an event id as processed in its own tx (poison isolation: failed
     * events are marked so the next sync tick does not reprocess them).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId) {
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, Instant.now(clock)));
        } catch (DataIntegrityViolationException dup) {
            // already marked; safe
        }
    }

    // ── private: processEvent body copied verbatim from existing SyncReceiverService ──

    private boolean processEvent(BuildingId buildingId, OutboxEventDto eventDto) throws JsonProcessingException {
        JsonNode payloadNode = objectMapper.readTree(eventDto.payload());

        if ("GAME_SESSION_COMPLETED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                // Malformed payload – skip stats update. Save is centralised in processOne.
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            Optional<Integer> durationSecondsOpt = extractDuration(payloadNode, eventDto.eventId());
            int durationSeconds = durationSecondsOpt.orElse(0);
            updateSessionStats(buildingId, gameType, periodStart, durationSeconds, eventDto.eventId());

            // FASE 3 — player read-model projection (participants + winnerId are
            // added to the payload by the Local GameSessionService.end). Guarded so
            // existing unit tests that construct this processor without a projection
            // (null) keep the historical behaviour; and so a payload that pre-dates
            // the enriched fields (no participants) is silently skipped without
            // affecting the aggregated_statistics update above.
            if (playerStatisticsProjection != null) {
                projectPlayerStatistics(buildingId, gameType, payloadNode, occurredAt, eventDto.eventId());
            }
            return true;

        } else if ("GAME_SESSION_ABORTED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            updateAbortedStats(buildingId, gameType, periodStart, eventDto.eventId());
            return true;

        } else if ("RESERVATION_CREATED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            updateReservationStats(buildingId, gameType, periodStart, 1, eventDto.eventId());
            return true;

        } else if ("RESERVATION_CANCELLED".equals(eventDto.eventType())) {
            ParsedGameTypePeriod parsed = parseGameTypePeriod(payloadNode, eventDto.eventId());
            if (parsed == null) {
                return false;
            }
            updateReservationStats(buildingId, parsed.gameType(), parsed.periodStart(), -1, eventDto.eventId());
            return true;
        } else if ("USER_REGISTERED".equals(eventDto.eventType())) {
            UserRegisteredEventDto dto = objectMapper.readValue(eventDto.payload(), UserRegisteredEventDto.class);
            registerUserFromSyncUseCase.registerFromSync(dto);
            return true;
        } else if ("TOURNAMENT_MATCH_COMPLETED".equals(eventDto.eventType())) {
            TournamentMatchResultDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentMatchResultDto.class);
            handleTournamentMatchCompleted(buildingId, dto);
            return true;
        } else if ("ROLE_ASSIGNMENT_REQUESTED".equals(eventDto.eventType())) {
            RoleAssignmentRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    RoleAssignmentRequestedEventDto.class);
            handleRoleAssignmentRequested(dto);
            return true;
        } else if ("GAME_DEFINITION_UPSERT_REQUESTED".equals(eventDto.eventType())) {
            GameDefinitionUpsertRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    GameDefinitionUpsertRequestedEventDto.class);
            handleGameDefinitionUpsertRequested(dto);
            return true;
        } else if ("TOURNAMENT_CREATE_REQUESTED".equals(eventDto.eventType())) {
            TournamentCreateRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentCreateRequestedEventDto.class);
            handleTournamentCreateRequested(dto);
            return true;
        } else if ("TOURNAMENT_OPEN_REQUESTED".equals(eventDto.eventType())) {
            TournamentLifecycleRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentLifecycleRequestedEventDto.class);
            handleTournamentOpenRequested(dto);
            return true;
        } else if ("TOURNAMENT_CANCEL_REQUESTED".equals(eventDto.eventType())) {
            TournamentLifecycleRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentLifecycleRequestedEventDto.class);
            handleTournamentCancelRequested(dto);
            return true;
        } else if ("TOURNAMENT_SCHEDULE_REQUESTED".equals(eventDto.eventType())) {
            TournamentLifecycleRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentLifecycleRequestedEventDto.class);
            handleTournamentScheduleRequested(dto);
            return true;
        } else if ("TOURNAMENT_UPDATE_REQUESTED".equals(eventDto.eventType())) {
            TournamentUpdateRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentUpdateRequestedEventDto.class);
            handleTournamentUpdateRequested(dto);
            return true;
        } else if ("TOURNAMENT_DELETE_REQUESTED".equals(eventDto.eventType())) {
            TournamentDeleteRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentDeleteRequestedEventDto.class);
            handleTournamentDeleteRequested(dto);
            return true;
        } else if ("PARTICIPANT_REGISTER_REQUESTED".equals(eventDto.eventType())) {
            ParticipantRegisterRequestedEventDto dto = objectMapper.readValue(eventDto.payload(),
                    ParticipantRegisterRequestedEventDto.class);
            handleParticipantRegisterRequested(dto);
            return true;
        }

        // Unknown eventType: mark as processed to avoid re-processing, but log a warning
        // so that unrecognised event types are not silently swallowed (audit trail).
        log.warn("Unknown eventType '{}' from building {} – marking processed without stats update.",
                eventDto.eventType(), buildingId);
        return true;
    }

    /**
     * Handles a {@code TOURNAMENT_MATCH_COMPLETED} event from a local server:
     * (a) loads the match via
     *     {@code tournamentMatchRepository.findByIdForUpdate(new TournamentMatchId(dto.matchId()))};
     * (b) rebuilds the {@link TournamentMatch} with
     *     {@code status = "ABANDONED".equals(dto.status()) ? ABANDONED : COMPLETED},
     *     {@code winner = dto.winner()}, {@code resultData = dto.resultData()},
     *     {@code playedAt = Instant.now(clock)} and saves it;
     * (c) if {@code status == COMPLETED} →
     *     {@code tournamentStandingsService.recomputeAfterCompletion(matchId)};
     * (d) {@code AdvanceOutcome outcome = tournamentBracketService.advanceWinner(matchId, dto.winner())};
     *     per Q2, {@code dto.winner()} is non-null even for ABANDONED (the Local
     *     side sends the walkover winner);
     * (e) if {@code outcome == WAS_FINAL} →
     *     {@code tournamentBracketService.completeIfDone(tournamentId)};
     *     if {@code outcome == NO_WINNER} → log.error and skip completion;
     *     if {@code outcome == PARENT_PATCHED} → log.info (no completion).
     *
     * <p>Runs inside the existing {@code @Transactional(REQUIRES_NEW)} of
     * {@link #processOne} — match update + standings recompute + bracket
     * advancement + (optional) next-round outbox emission are atomic.</p>
     *
     * <p>Defensive: if the FASE 6 tournament ports are {@code null} (legacy
     * unit-test ctor), the branch logs a warning and returns without doing
     * any tournament bookkeeping.</p>
     */
    private void handleTournamentMatchCompleted(BuildingId buildingId,
                                                TournamentMatchResultDto dto) {
        if (tournamentMatchRepository == null || tournamentBracketService == null
                || tournamentStandingsService == null || tournamentRepository == null) {
            log.warn("TOURNAMENT_MATCH_COMPLETED [matchId={}] received but FASE 6 tournament ports are null "
                    + "(legacy test ctor) — skipping tournament bookkeeping.", dto.matchId());
            return;
        }
        TournamentMatchId matchId = new TournamentMatchId(dto.matchId());
        TournamentMatch match = tournamentMatchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tournament match not found for TOURNAMENT_MATCH_COMPLETED: " + dto.matchId()));

        TournamentMatchStatus status = TournamentMatchStatus.ABANDONED.name().equals(dto.status())
                ? TournamentMatchStatus.ABANDONED : TournamentMatchStatus.COMPLETED;
        TournamentMatch updated = new TournamentMatch(
                match.getMatchId(), match.getTournamentId(),
                match.getRound(), match.getBracketPosition(),
                match.getParticipantA(), match.getParticipantB(),
                match.getBuildingId(), match.getGameId(), match.getSessionId(),
                dto.winner(), status,
                match.getScheduledAt(), Instant.now(clock), dto.resultData());
        tournamentMatchRepository.save(updated);

        // (c) Recompute standings only for COMPLETED (ABANDONED is bookkeeping-only per Q2).
        if (status == TournamentMatchStatus.COMPLETED) {
            tournamentStandingsService.recomputeAfterCompletion(match.getMatchId());
        }

        // (d) Advance the winner into the parent slot (Q2: winnerId is non-null even for ABANDONED).
        TournamentBracketService.AdvanceOutcome outcome =
                tournamentBracketService.advanceWinner(match.getMatchId(), dto.winner());

        // (e) Branch on the outcome to decide whether to attempt tournament completion.
        if (outcome == TournamentBracketService.AdvanceOutcome.WAS_FINAL) {
            tournamentBracketService.completeIfDone(match.getTournamentId());
        } else if (outcome == TournamentBracketService.AdvanceOutcome.NO_WINNER) {
            log.error("TOURNAMENT_MATCH_COMPLETED [matchId={}] received with null winner on a COMPLETED match "
                    + "with both participants present — skipping bracket advancement and tournament completion "
                    + "(tournament stays IN_PROGRESS).", dto.matchId());
        } else {
            log.info("TOURNAMENT_MATCH_COMPLETED [matchId={}] advanced winner to parent match.",
                    dto.matchId());
        }
    }

    /**
     * Handles a {@code ROLE_ASSIGNMENT_REQUESTED} event from a Local admin use
     * case (PIANO §7.A.7 / RF-UT-02): delegates to
     * {@link UpdateUserUseCase#updateUser} with {@code newPassword=null} and the
     * new roles, passing {@code dto.requestId()} as {@code originatingRequestId}
     * so the resulting {@code USER_UPDATED} outbox event carries it back to the
     * Local for {@code admin_requests_local.markCompleted}. Idempotency is
     * ensured by {@code processed_events} on the Central side.
     */
    private void handleRoleAssignmentRequested(RoleAssignmentRequestedEventDto dto) {
        if (updateUserUseCase == null) {
            log.warn("ROLE_ASSIGNMENT_REQUESTED [targetUserId={}] received but UpdateUserUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.targetUserId());
            return;
        }
        updateUserUseCase.updateUser(new UserId(dto.targetUserId()), null, dto.roles(), dto.requestId());
    }

    /**
     * Handles a {@code GAME_DEFINITION_UPSERT_REQUESTED} event: builds a
     * {@link GameDefinition} from the payload and delegates to
     * {@link UpsertGameDefinitionUseCase#upsert} with {@code dto.requestId()}
     * as {@code originatingRequestId}. The resulting
     * {@code GAME_DEFINITION_UPSERTED} outbox event carries it back to the Local.
     */
    private void handleGameDefinitionUpsertRequested(GameDefinitionUpsertRequestedEventDto dto) {
        if (upsertGameDefinitionUseCase == null) {
            log.warn("GAME_DEFINITION_UPSERT_REQUESTED [gameType={}] received but UpsertGameDefinitionUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.gameType());
            return;
        }
        Instant now = Instant.now(clock);
        GameDefinition input = new GameDefinition(
                dto.gameType(),
                dto.name(),
                dto.minPlayers(),
                dto.maxPlayers(),
                dto.teamAllowed(),
                dto.registrationRules(),
                dto.createdAt() != null ? dto.createdAt() : now,
                now);
        upsertGameDefinitionUseCase.upsert(input, dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_CREATE_REQUESTED} event: builds a DRAFT
     * {@link Tournament} from the payload (createdBy = actingUserId) and
     * delegates to {@link CreateTournamentUseCase#create} with
     * {@code dto.requestId()} as {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carries it back.
     */
    private void handleTournamentCreateRequested(TournamentCreateRequestedEventDto dto) {
        if (createTournamentUseCase == null) {
            log.warn("TOURNAMENT_CREATE_REQUESTED [name={}] received but CreateTournamentUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.name());
            return;
        }
        Instant now = Instant.now(clock);
        Tournament input = new Tournament(
                new TournamentId(UUID.randomUUID().toString()),
                dto.name(),
                dto.gameType(),
                dto.teamBased(),
                dto.teamSize(),
                TournamentFormat.SINGLE_ELIMINATION,
                TournamentStatus.DRAFT,
                dto.startsAt(),
                null,
                new UserId(dto.actingUserId()),
                dto.createdAt() != null ? dto.createdAt() : now);
        createTournamentUseCase.create(input, dto.buildingIds(), dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_OPEN_REQUESTED} event: delegates to
     * {@link OpenTournamentRegistrationUseCase#open} with
     * {@code dto.requestId()} as {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carries it back.
     */
    private void handleTournamentOpenRequested(TournamentLifecycleRequestedEventDto dto) {
        if (openTournamentRegistrationUseCase == null) {
            log.warn("TOURNAMENT_OPEN_REQUESTED [tournamentId={}] received but OpenTournamentRegistrationUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        openTournamentRegistrationUseCase.open(new TournamentId(dto.tournamentId()), dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_CANCEL_REQUESTED} event: delegates to
     * {@link CancelTournamentUseCase#cancel} with {@code dto.requestId()} as
     * {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carries it back.
     */
    private void handleTournamentCancelRequested(TournamentLifecycleRequestedEventDto dto) {
        if (cancelTournamentUseCase == null) {
            log.warn("TOURNAMENT_CANCEL_REQUESTED [tournamentId={}] received but CancelTournamentUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        cancelTournamentUseCase.cancel(new TournamentId(dto.tournamentId()), dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_SCHEDULE_REQUESTED} event: delegates to
     * {@link ScheduleTournamentMatchesUseCase#schedule} (which transitions the
     * tournament {@code OPEN_REGISTRATION -> IN_PROGRESS}, persists the round-1
     * bracket and emits one {@code TOURNAMENT_MATCH_SCHEDULED} outbox event per
     * SCHEDULED match), then emits a single
     * {@code TOURNAMENT_SUMMARY_UPSERTED} return event carrying
     * {@code dto.requestId()} as {@code originatingRequestId} and the updated
     * tournament snapshot (status {@code IN_PROGRESS}).
     *
     * <p>The {@code TOURNAMENT_MATCH_SCHEDULED} rows do NOT carry the
     * originatingRequestId (the schedule use case signature has no such param
     * and threading it would ripple through the bracket/advance path), so
     * without this summary emit the Local {@code admin_requests_local} row for
     * SCHEDULE would stay PENDING until the timeout, surfacing as FAILED to the
     * admin despite a successful schedule (BUG-SCHEDULE-REQUEST-ID). The
     * Local {@code TournamentSummarySyncService.markCompletedIfRequested}
     * closes the row as COMPLETED on receiving the summary upsert with a
     * non-null {@code originatingRequestId}, independently of the summary
     * payload content.</p>
     *
     * <p>Defensive: if {@code emitTournamentSummaryUseCase} is {@code null}
     * (legacy 11-arg test ctor), logs a warning and skips the summary emit
     * (historical behaviour for legacy tests).</p>
     */
    private void handleTournamentScheduleRequested(TournamentLifecycleRequestedEventDto dto) {
        if (scheduleTournamentMatchesUseCase == null) {
            log.warn("TOURNAMENT_SCHEDULE_REQUESTED [tournamentId={}] received but ScheduleTournamentMatchesUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        scheduleTournamentMatchesUseCase.schedule(new TournamentId(dto.tournamentId()));
        if (emitTournamentSummaryUseCase == null) {
            log.warn("TOURNAMENT_SCHEDULE_REQUESTED [tournamentId={}] scheduled but EmitTournamentSummaryUseCase is null "
                    + "(legacy test ctor) — skipping TOURNAMENT_SUMMARY_UPSERTED return emit; "
                    + "admin_requests_local closure for SCHEDULE will NOT fire in this test context.",
                    dto.tournamentId());
            return;
        }
        emitTournamentSummaryUseCase.emitSummary(new TournamentId(dto.tournamentId()), dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_UPDATE_REQUESTED} event: delegates to
     * {@link UpdateTournamentUseCase#update} with {@code dto.requestId()} as
     * {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carries it back.
     */
    private void handleTournamentUpdateRequested(TournamentUpdateRequestedEventDto dto) {
        if (updateTournamentUseCase == null) {
            log.warn("TOURNAMENT_UPDATE_REQUESTED [tournamentId={}] received but UpdateTournamentUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        updateTournamentUseCase.update(new TournamentId(dto.tournamentId()), dto.name(), dto.startsAt(),
                dto.buildingIds(), dto.requestId());
    }

    /**
     * Handles a {@code TOURNAMENT_DELETE_REQUESTED} event: delegates to
     * {@link DeleteTournamentUseCase#delete} with {@code dto.requestId()} as
     * {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_SUMMARY_UPSERTED} tombstone outbox event carries it back.
     */
    private void handleTournamentDeleteRequested(TournamentDeleteRequestedEventDto dto) {
        if (deleteTournamentUseCase == null) {
            log.warn("TOURNAMENT_DELETE_REQUESTED [tournamentId={}] received but DeleteTournamentUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        deleteTournamentUseCase.delete(new TournamentId(dto.tournamentId()), dto.requestId());
    }

    /**
     * Handles a {@code PARTICIPANT_REGISTER_REQUESTED} event from a Local
     * PLAYER use case (PIANO §7.B W6): delegates to
     * {@link RegisterTournamentParticipantUseCase#register} with the acting
     * player as captain and {@code dto.requestId()} as
     * {@code originatingRequestId}. The resulting
     * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} outbox event carries it back to
     * the Local for {@code admin_requests_local.markCompleted}.
     */
    private void handleParticipantRegisterRequested(ParticipantRegisterRequestedEventDto dto) {
        if (registerTournamentParticipantUseCase == null) {
            log.warn("PARTICIPANT_REGISTER_REQUESTED [tournamentId={}] received but RegisterTournamentParticipantUseCase is null "
                    + "(legacy test ctor) — skipping.", dto.tournamentId());
            return;
        }
        registerTournamentParticipantUseCase.register(
                new TournamentId(dto.tournamentId()),
                new UserId(dto.actingUserId()),
                dto.teamName(),
                dto.teamMemberIds(),
                dto.requestId());
    }

    /**
     * Safely parses the gameType field from the payload node.
     * Returns {@code null} and logs a warning if the field is missing or contains an invalid value.
     */
    @Nullable
    private GameType parseGameType(JsonNode payloadNode, String eventId) {
        String gameTypeStr = payloadNode.has("gameType") ? payloadNode.get("gameType").asText() : null;
        if (gameTypeStr == null || gameTypeStr.isBlank()) {
            log.warn("Sync event [{}] is missing 'gameType' field – skipping stats update.", eventId);
            return null;
        }
        try {
            return GameType.valueOf(gameTypeStr);
        } catch (IllegalArgumentException e) {
            log.warn("Sync event [{}] has unrecognised gameType '{}' – skipping stats update.", eventId, gameTypeStr);
            return null;
        }
    }

    @Nullable
    private ParsedGameTypePeriod parseGameTypePeriod(JsonNode payloadNode, String eventId) {
        GameType gameType = parseGameType(payloadNode, eventId);
        if (gameType == null) {
            return null;
        }
        Instant occurredAt = payloadNode.has("occurredAt")
                ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
        LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
        return new ParsedGameTypePeriod(gameType, periodStart);
    }

    private record ParsedGameTypePeriod(GameType gameType, LocalDate periodStart) {}

    private Optional<Integer> extractDuration(JsonNode payloadNode, String eventId) throws JsonProcessingException {
        if (payloadNode.has("durationSeconds")) {
            JsonNode n = payloadNode.get("durationSeconds");
            if (isUsableInt(n)) {
                return Optional.of(n.asInt());
            }
            String failure = (n == null || n.isNull()) ? "null" : "non-numeric: " + n.toString();
            log.warn("Event [{}] 'durationSeconds' present but {} – assuming 0 for statistics.", eventId, failure);
            return Optional.empty();
        }
        if (payloadNode.has("resultJson")) {
            JsonNode resultNode = objectMapper.readTree(payloadNode.get("resultJson").asText());
            if (resultNode.has("durationSeconds")) {
                JsonNode n = resultNode.get("durationSeconds");
                if (isUsableInt(n)) {
                    return Optional.of(n.asInt());
                }
                String failure = (n == null || n.isNull()) ? "null" : "non-numeric: " + n.toString();
                log.warn("Event [{}] resultJson.durationSeconds present but {} – assuming 0 for statistics.", eventId, failure);
                return Optional.empty();
            }
            if (resultNode.has("duration_s")) {
                JsonNode n = resultNode.get("duration_s");
                if (isUsableInt(n)) {
                    return Optional.of(n.asInt());
                }
                String failure = (n == null || n.isNull()) ? "null" : "non-numeric: " + n.toString();
                log.warn("Event [{}] resultJson.duration_s present but {} – assuming 0 for statistics.", eventId, failure);
                return Optional.empty();
            }
        }
        log.warn("Event [{}] missing 'durationSeconds' (resultJson fallback missing) – assuming 0 for statistics.", eventId);
        return Optional.empty();
    }

    private static boolean isUsableInt(JsonNode n) {
        return n != null && !n.isNull() && n.isNumber() && n.canConvertToInt();
    }

    /**
     * FASE 3 — projects a {@code GAME_SESSION_COMPLETED} event into the
     * per-player read-models via {@link PlayerStatisticsProjectionService}.
     *
     * <p>Defensive parsing: a payload missing {@code sessionId} or
     * {@code participants} (e.g. an event emitted by a Local Server that has not
     * yet been upgraded with the enriched &sect;2.2 fields) is skipped with a log
     * line and does <strong>not</strong> throw, so the aggregated-statistics
     * update that already ran in this transaction is preserved. Only genuine
     * persistence failures propagate (and roll back the whole transaction, per
     * poison-isolation), which keeps {@code player_match_facts} /
     * {@code player_statistics} atomic with each other.</p>
     */
    private void projectPlayerStatistics(BuildingId buildingId, GameType gameType,
                                         JsonNode payloadNode, Instant endedAt, String eventId) {
        String sessionId = payloadNode.has("sessionId") ? payloadNode.get("sessionId").asText() : null;
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Sync event [{}] GAME_SESSION_COMPLETED missing 'sessionId' – skipping player read-model projection.", eventId);
            return;
        }
        List<String> participants = parseParticipants(payloadNode);
        if (participants.isEmpty()) {
            log.debug("Sync event [{}] GAME_SESSION_COMPLETED carries no participants – skipping player read-model projection.", eventId);
            return;
        }
        String winnerId = (payloadNode.has("winnerId") && !payloadNode.get("winnerId").isNull())
                ? payloadNode.get("winnerId").asText() : null;
        WinCondition winCondition = parseWinCondition(payloadNode, eventId);
        playerStatisticsProjection.onGameSessionCompleted(
                buildingId, gameType, sessionId, participants, winnerId, winCondition, endedAt);
    }

    private static List<String> parseParticipants(JsonNode payloadNode) {
        if (!payloadNode.has("participants")) {
            return List.of();
        }
        JsonNode node = payloadNode.get("participants");
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<String> participants = new ArrayList<>();
        for (JsonNode elem : node) {
            if (elem != null && !elem.isNull()) {
                String uid = elem.asText();
                if (uid != null && !uid.isBlank()) {
                    participants.add(uid);
                }
            }
        }
        return participants;
    }

    private WinCondition parseWinCondition(JsonNode payloadNode, String eventId) {
        if (!payloadNode.has("winCondition") || payloadNode.get("winCondition").isNull()) {
            return null;
        }
        String wcStr = payloadNode.get("winCondition").asText();
        if (wcStr == null || wcStr.isBlank()) {
            return null;
        }
        try {
            return WinCondition.valueOf(wcStr);
        } catch (IllegalArgumentException e) {
            log.warn("Sync event [{}] has unrecognised winCondition '{}' – storing null on the player match fact.", eventId, wcStr);
            return null;
        }
    }

    /**
     * Updates session statistics using a pessimistic write lock to prevent lost updates
     * when multiple sync requests arrive concurrently for the same building/game/period.
     */
    private void updateSessionStats(BuildingId buildingId, GameType gameType, LocalDate period, int durationSeconds, String eventId) {
        // Use locked query to prevent concurrent lost-update race conditions (TOCTOU)
        Optional<AggregatedStatistics> existing =
                statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(buildingId, gameType, period);

        if (existing.isPresent()) {
            AggregatedStatistics stats = existing.get();
            AggregatedStatistics sessionStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    1,
                    durationSeconds,
                    0,
                    new java.util.HashMap<>()
            );
            stats.mergeWith(sessionStats);
            statisticsRepository.save(stats);
        } else {
            AggregatedStatistics newStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    1,
                    durationSeconds,
                    0,
                    new java.util.HashMap<>()
            );
            try {
                statisticsRepository.save(newStats);
            } catch (DataIntegrityViolationException dup) {
                log.info("First-bucket race on aggregated_statistics insert [{}|{}|{}], retrying in fresh tx",
                        buildingId, gameType, period);
                if (entityManager != null) {
                    entityManager.clear();
                }
                retryHelper.retryMergeAndMarkProcessed(buildingId, gameType, period, newStats, eventId);
                throw new FirstBucketRaceHandledException(
                        "First-bucket race resolved for [" + buildingId + "|" + gameType + "|" + period + "]");
            }
        }
    }

    /**
     * Updates aborted-session statistics using a pessimistic write lock.
     * Aborted sessions (TIMEOUT / SERVER_RESTART) are counted separately from
     * completed sessions: they increment {@code totalAbortedSessions} but NOT
     * {@code totalSessions}, so average duration and completion counts are not
     * distorted by sessions that did not reach a natural end.
     */
    private void updateAbortedStats(BuildingId buildingId, GameType gameType, LocalDate period, String eventId) {
        Optional<AggregatedStatistics> existing =
                statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(buildingId, gameType, period);

        if (existing.isPresent()) {
            AggregatedStatistics stats = existing.get();
            AggregatedStatistics abortedStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    0,
                    0,
                    0,
                    1,
                    new java.util.HashMap<>()
            );
            stats.mergeWith(abortedStats);
            statisticsRepository.save(stats);
        } else {
            AggregatedStatistics newStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    0,
                    0,
                    0,
                    1,
                    new java.util.HashMap<>()
            );
            try {
                statisticsRepository.save(newStats);
            } catch (DataIntegrityViolationException dup) {
                log.info("First-bucket race on aggregated_statistics insert [{}|{}|{}], retrying in fresh tx",
                        buildingId, gameType, period);
                if (entityManager != null) {
                    entityManager.clear();
                }
                retryHelper.retryMergeAndMarkProcessed(buildingId, gameType, period, newStats, eventId);
                throw new FirstBucketRaceHandledException(
                        "First-bucket race resolved for [" + buildingId + "|" + gameType + "|" + period + "]");
            }
        }
    }

    /**
     * Updates reservation statistics using a pessimistic write lock.
     */
    private void updateReservationStats(BuildingId buildingId, GameType gameType, LocalDate period, int reservationDelta, String eventId) {
        // Use locked query to prevent concurrent lost-update race conditions (TOCTOU)
        Optional<AggregatedStatistics> existing =
                statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(buildingId, gameType, period);

        if (existing.isPresent()) {
            AggregatedStatistics stats = existing.get();
            int newReservations = Math.max(0, stats.getTotalReservations() + reservationDelta);
            stats = new AggregatedStatistics(
                    stats.getId(),
                    stats.getBuildingId(),
                    stats.getGameType(),
                    stats.getPeriodStart(),
                    stats.getPeriodEnd(),
                    stats.getTotalSessions(),
                    stats.getAvgDurationSeconds(),
                    newReservations,
                    stats.getTotalAbortedSessions(),
                    stats.getData()
            );
            statisticsRepository.save(stats);
        } else {
            int initialReservations = Math.max(0, reservationDelta);
            AggregatedStatistics newStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    0,
                    0,
                    initialReservations,
                    0,
                    new java.util.HashMap<>()
            );
            try {
                statisticsRepository.save(newStats);
            } catch (DataIntegrityViolationException dup) {
                log.info("First-bucket race on aggregated_statistics insert [{}|{}|{}], retrying in fresh tx",
                        buildingId, gameType, period);
                if (entityManager != null) {
                    entityManager.clear();
                }
                retryHelper.retryMergeAndMarkProcessed(buildingId, gameType, period, newStats, eventId);
                throw new FirstBucketRaceHandledException(
                        "First-bucket race resolved for [" + buildingId + "|" + gameType + "|" + period + "]");
            }
        }
    }
}
