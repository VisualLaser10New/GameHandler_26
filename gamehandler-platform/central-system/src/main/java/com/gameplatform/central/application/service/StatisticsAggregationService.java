package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.in.GetGlobalStatisticsUseCase;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsAggregationService implements GetGlobalStatisticsUseCase {
    private final StatisticsRepository repository;
    private final ObjectMapper objectMapper;

    public StatisticsAggregationService(StatisticsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end) {
        List<AggregatedStatistics> rawStats = repository.findByPeriod(buildingId, gameType, start, end);

        return rawStats.stream().map(this::toDto).collect(Collectors.toList());
    }

    private StatisticsDto toDto(AggregatedStatistics stats) {
        String jsonData = null;
        if (stats.getData() != null && !stats.getData().isEmpty()) {
            try {
                jsonData = objectMapper.writeValueAsString(stats.getData());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize statistics metadata map", e);
            }
        }

        return new StatisticsDto(
                stats.getBuildingId() != null ? stats.getBuildingId().id() : null,
                stats.getGameType() != null ? stats.getGameType().name() : null,
                stats.getPeriodStart() != null ? stats.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                stats.getPeriodEnd() != null ? stats.getPeriodEnd().atTime(java.time.LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant() : null,
                stats.getTotalSessions(),
                stats.getAvgDurationSeconds(),
                stats.getTotalReservations(),
                jsonData,
                stats.getTotalAbortedSessions()
        );
    }
}
