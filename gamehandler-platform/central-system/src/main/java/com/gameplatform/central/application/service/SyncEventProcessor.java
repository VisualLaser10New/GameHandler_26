package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.DuplicateEventException;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
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

    public SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                              StatisticsRepository statisticsRepository,
                              RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.statisticsRepository = statisticsRepository;
        this.registerUserFromSyncUseCase = registerUserFromSyncUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
            int durationSeconds = extractDuration(payloadNode, eventDto.eventId());
            updateSessionStats(buildingId, gameType, periodStart, durationSeconds);
            return true;

        } else if ("GAME_SESSION_ABORTED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            updateAbortedStats(buildingId, gameType, periodStart);
            return true;

        } else if ("RESERVATION_CREATED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            updateReservationStats(buildingId, gameType, periodStart, 1);
            return true;

        } else if ("RESERVATION_CANCELLED".equals(eventDto.eventType())) {
            ParsedGameTypePeriod parsed = parseGameTypePeriod(payloadNode, eventDto.eventId());
            if (parsed == null) {
                return false;
            }
            updateReservationStats(buildingId, parsed.gameType(), parsed.periodStart(), -1);
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

    private int extractDuration(JsonNode payloadNode, String eventId) throws JsonProcessingException {
        if (payloadNode.has("durationSeconds")) {
            return payloadNode.get("durationSeconds").asInt();
        }
        if (payloadNode.has("resultJson")) {
            String resultJson = payloadNode.get("resultJson").asText();
            JsonNode resultNode = objectMapper.readTree(resultJson);
            if (resultNode.has("durationSeconds")) {
                return resultNode.get("durationSeconds").asInt();
            }
            if (resultNode.has("duration_s")) {
                return resultNode.get("duration_s").asInt();
            }
        }
        log.warn("Event [{}] missing 'durationSeconds' field – assuming 0 for statistics.", eventId);
        return 0;
    }

    /**
     * Updates session statistics using a pessimistic write lock to prevent lost updates
     * when multiple sync requests arrive concurrently for the same building/game/period.
     */
    private void updateSessionStats(BuildingId buildingId, GameType gameType, LocalDate period, int durationSeconds) {
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
            statisticsRepository.save(newStats);
        }
    }

    /**
     * Updates aborted-session statistics using a pessimistic write lock.
     * Aborted sessions (TIMEOUT / SERVER_RESTART) are counted separately from
     * completed sessions: they increment {@code totalAbortedSessions} but NOT
     * {@code totalSessions}, so average duration and completion counts are not
     * distorted by sessions that did not reach a natural end.
     */
    private void updateAbortedStats(BuildingId buildingId, GameType gameType, LocalDate period) {
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
            statisticsRepository.save(newStats);
        }
    }

    /**
     * Updates reservation statistics using a pessimistic write lock.
     */
    private void updateReservationStats(BuildingId buildingId, GameType gameType, LocalDate period, int reservationDelta) {
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
            statisticsRepository.save(newStats);
        }
    }
}
