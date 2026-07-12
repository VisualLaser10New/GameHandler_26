package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.FirstBucketRaceHandledException;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.OutboxEventDto;
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

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                              StatisticsRepository statisticsRepository,
                              RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                              ObjectMapper objectMapper,
                              Clock clock,
                              StatisticsFirstBucketRaceRetryHelper retryHelper,
                              PlayerStatisticsProjectionService playerStatisticsProjection) {
        this.processedEventRepository = processedEventRepository;
        this.statisticsRepository = statisticsRepository;
        this.registerUserFromSyncUseCase = registerUserFromSyncUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retryHelper = retryHelper;
        this.playerStatisticsProjection = playerStatisticsProjection;
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
        }

        // Unknown eventType: mark as processed to avoid re-processing, but log a warning
        // so that unrecognised event types are not silently swallowed (audit trail).
        log.warn("Unknown eventType '{}' from building {} – marking processed without stats update.",
                eventDto.eventType(), buildingId);
        return true;
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
