package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

public class SyncReceiverService implements ReceiveSyncDataUseCase {
    private final ProcessedEventRepository processedEventRepository;
    private final StatisticsRepository statisticsRepository;
    private final ObjectMapper objectMapper;

    public SyncReceiverService(ProcessedEventRepository processedEventRepository, StatisticsRepository statisticsRepository, ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.statisticsRepository = statisticsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Override
    public void receiveSyncPayload(SyncPayloadDto payload) {
        if (payload != null && payload.events() != null && !payload.events().isEmpty()) {
            BuildingId buildingId = new BuildingId(payload.buildingId());

            for (OutboxEventDto event : payload.events()) {
                try {
                    boolean processed = processEvent(buildingId, event);

                    if (processed) {
                        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.createdAt()));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Error processing sync event: " + event.eventId(), e);
                }
            }
        }
    }

    private boolean processEvent(BuildingId buildingId, OutboxEventDto eventDto) throws JsonProcessingException {
        JsonNode payloadNode = objectMapper.readTree(eventDto.payload());
        if ("GAME_SESSION_COMPLETED".equals(eventDto.eventType())) {
            String gameTypeStr = payloadNode.has("gameType") ? payloadNode.get("gameType").asText() : null;
            if (gameTypeStr == null) {
                return false;
            }
            GameType gameType = GameType.valueOf(gameTypeStr);
            Instant occurredAt = payloadNode.has("occurredAt") ?
                    Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now();
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            // Estrazione durata della sessione
            int durationSeconds = 0;
            if (payloadNode.has("durationSeconds")) {
                durationSeconds = payloadNode.get("durationSeconds").asInt();
            } else if (payloadNode.has("resultJson")) {
                String resultJson = payloadNode.get("resultJson").asText();
                JsonNode resultNode = objectMapper.readTree(resultJson);
                if (resultNode.has("durationSeconds")) {
                    durationSeconds = resultNode.get("durationSeconds").asInt();
                } else if (resultNode.has("duration_s")) {
                    durationSeconds = resultNode.get("duration_s").asInt();
                }
            }
            updateSessionStats(buildingId, gameType, periodStart, durationSeconds);
            return true;
        } else if ("RESERVATION_CREATED".equals(eventDto.eventType())) {
            String gameTypeStr = payloadNode.has("gameType") ? payloadNode.get("gameType").asText() : null;
            if (gameTypeStr == null) {
                return false;
            }
            GameType gameType = GameType.valueOf(gameTypeStr);
            Instant occurredAt = payloadNode.has("occurredAt") ?
                    Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now();
            LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
            updateReservationStats(buildingId, gameType, periodStart, 1);
            return true;
        } else if ("RESERVATION_CANCELLED".equals(eventDto.eventType())) {
            checkGameType result = getCheckGameType(payloadNode);
            if (result == null) return false;
            updateReservationStats(buildingId, result.gameType(), result.periodStart(), -1);
            return true;
        }
        // Segnamo come "elaborato" anche altri eventi minori per evitarne la rielaborazione
        return true;
    }

    @Nullable
    private static checkGameType getCheckGameType(JsonNode payloadNode) {
        String gameTypeStr = payloadNode.has("gameType") ? payloadNode.get("gameType").asText() : null;
        if (gameTypeStr == null) {
            return null;
        }
        GameType gameType = GameType.valueOf(gameTypeStr);
        Instant occurredAt = payloadNode.has("occurredAt") ?
                Instant.parse(payloadNode.get("occurredAt").asText()) : Instant.now();
        LocalDate periodStart = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
        checkGameType result = new checkGameType(gameType, periodStart);
        return result;
    }

    private record checkGameType(GameType gameType, LocalDate periodStart) {
    }

    private void updateSessionStats(BuildingId buildingId, GameType gameType, LocalDate period, int durationSeconds) {
        Optional<AggregatedStatistics> existing = statisticsRepository.findByBuildingAndTypeAndPeriod(buildingId, gameType, period);
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
    private void updateReservationStats(BuildingId buildingId, GameType gameType, LocalDate period, int reservationDelta) {
        Optional<AggregatedStatistics> existing = statisticsRepository.findByBuildingAndTypeAndPeriod(buildingId, gameType, period);
        if (existing.isPresent()) {
            AggregatedStatistics stats = existing.get();
            AggregatedStatistics reservationStats = new AggregatedStatistics(
                    UUID.randomUUID().toString(),
                    buildingId,
                    gameType,
                    period,
                    period,
                    0,
                    0,
                    reservationDelta,
                    new java.util.HashMap<>()
            );
            stats.mergeWith(reservationStats);
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
