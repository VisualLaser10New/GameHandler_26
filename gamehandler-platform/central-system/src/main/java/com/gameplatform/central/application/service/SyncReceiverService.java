package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.DuplicateEventException;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that receives and processes sync payloads from local servers.
 *
 * <p>Concurrency-safe: uses pessimistic write locks when reading statistics to prevent
 * lost updates on concurrent syncs for the same building/game-type/period.</p>
 *
 * <p>Resilience: malformed or unrecognised gameType values are silently skipped after
 * logging a warning, so one bad event never poisons the entire sync batch.</p>
 */
@Service
public class SyncReceiverService implements ReceiveSyncDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncReceiverService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final StatisticsRepository statisticsRepository;
    private final LocalServerRegistryPort localServerRegistryPort;
    private final RegisterUserFromSyncUseCase registerUserFromSyncUseCase;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SyncReceiverService(
            ProcessedEventRepository processedEventRepository,
            StatisticsRepository statisticsRepository,
            LocalServerRegistryPort localServerRegistryPort,
            RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
            ObjectMapper objectMapper) {
        this(processedEventRepository, statisticsRepository, localServerRegistryPort, registerUserFromSyncUseCase, objectMapper, Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SyncReceiverService(
            ProcessedEventRepository processedEventRepository,
            StatisticsRepository statisticsRepository,
            LocalServerRegistryPort localServerRegistryPort,
            RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
            ObjectMapper objectMapper,
            Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.statisticsRepository = statisticsRepository;
        this.localServerRegistryPort = localServerRegistryPort;
        this.registerUserFromSyncUseCase = registerUserFromSyncUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void receiveSyncPayload(SyncPayloadDto payload) {
        if (payload == null || payload.events() == null || payload.events().isEmpty()) {
            return;
        }

        BuildingId buildingId = new BuildingId(payload.buildingId());

        for (OutboxEventDto event : payload.events()) {
            try {
                if (processedEventRepository.existsByEventId(event.eventId())) {
                    log.info("Duplicate sync event caught, skipping: {}", event.eventId());
                    continue;
                }

                boolean processed = processEvent(buildingId, event);

                if (processed) {
                    processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));
                }
            } catch (Exception e) {
                log.error("Failed to parse/process sync event [{}] due to exception: {}. Skipping event and marking it as processed to prevent batch poisoning.",
                        event.eventId(), e.getMessage(), e);
                try {
                    processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now(clock)));
                } catch (Exception saveEx) {
                    log.error("Failed to mark failed event [{}] as processed: {}", event.eventId(), saveEx.getMessage(), saveEx);
                }
            }
        }

        // Heartbeat: update lastSeenAt for this building's server after a successful sync
        localServerRegistryPort.updateLastSeenAt(buildingId, Instant.now(clock));
    }

    private boolean processEvent(BuildingId buildingId, OutboxEventDto eventDto) throws JsonProcessingException {
        JsonNode payloadNode = objectMapper.readTree(eventDto.payload());

        if ("GAME_SESSION_COMPLETED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                // Malformed payload – record as processed to prevent re-processing, but skip stats update
                processedEventRepository.save(new ProcessedEvent(eventDto.eventId(), Instant.now(clock)));
                return false;
            }
            Instant occurredAt = payloadNode.has("occurredAt")
                    ? Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now(clock);
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            int durationSeconds = extractDuration(payloadNode);
            updateSessionStats(buildingId, gameType, periodStart, durationSeconds);
            return true;

        } else if ("RESERVATION_CREATED".equals(eventDto.eventType())) {
            GameType gameType = parseGameType(payloadNode, eventDto.eventId());
            if (gameType == null) {
                processedEventRepository.save(new ProcessedEvent(eventDto.eventId(), Instant.now(clock)));
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
                processedEventRepository.save(new ProcessedEvent(eventDto.eventId(), Instant.now(clock)));
                return false;
            }
            updateReservationStats(buildingId, parsed.gameType(), parsed.periodStart(), -1);
            return true;
        } else if ("USER_REGISTERED".equals(eventDto.eventType())) {
            UserRegisteredEventDto dto = objectMapper.readValue(eventDto.payload(), UserRegisteredEventDto.class);
            registerUserFromSyncUseCase.registerFromSync(dto);
            return true;
        }

        // Mark other minor events as processed to avoid re-processing
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

    private int extractDuration(JsonNode payloadNode) throws JsonProcessingException {
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
                    new java.util.HashMap<>()
            );
            statisticsRepository.save(newStats);
        }
    }
}
