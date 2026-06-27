package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class StatisticsMapper {

    private static final Logger log = LoggerFactory.getLogger(StatisticsMapper.class);

    private final ObjectMapper objectMapper;

    public StatisticsMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AggregatedStatistics toDomain(AggregatedStatisticsJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> dataMap = new HashMap<>();
        if (entity.getData() != null && !entity.getData().isBlank()) {
            try {
                dataMap = objectMapper.readValue(entity.getData(), new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                log.error("Critical error parsing statistics JSON data for entity ID: {}", entity.getId(), e);
                throw new RuntimeException("Failed to deserialize AggregatedStatistics data", e);
            }
        }
        return new AggregatedStatistics(
                entity.getId(),
                new BuildingId(entity.getBuildingId()),
                GameType.valueOf(entity.getGameType()),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getTotalSessions(),
                entity.getAvgDurationSeconds(),
                entity.getTotalReservations(),
                dataMap
        );
    }

    public AggregatedStatisticsJpaEntity toEntity(AggregatedStatistics domain) {
        if (domain == null) {
            return null;
        }
        String dataStr = "{}";
        if (domain.getData() != null) {
            try {
                dataStr = objectMapper.writeValueAsString(domain.getData());
            } catch (IOException e) {
                log.error("Critical error serializing statistics JSON data for domain ID: {}", domain.getId(), e);
                throw new RuntimeException("Failed to serialize AggregatedStatistics data", e);
            }
        }
        return new AggregatedStatisticsJpaEntity(
                domain.getId(),
                domain.getBuildingId() != null ? domain.getBuildingId().id() : null,
                domain.getGameType() != null ? domain.getGameType().name() : null,
                domain.getPeriodStart(),
                domain.getPeriodEnd(),
                domain.getTotalSessions(),
                domain.getAvgDurationSeconds(),
                domain.getTotalReservations(),
                dataStr
        );
    }
}
