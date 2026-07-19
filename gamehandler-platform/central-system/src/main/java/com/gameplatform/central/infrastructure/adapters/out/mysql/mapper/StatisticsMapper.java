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

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link AggregatedStatistics} e l'entità persistente
 * {@link AggregatedStatisticsJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, utilizza un
 * {@link ObjectMapper} iniettato per serializzare/deserializzare
 * la colonna JSON {@code data} da/verso una {@link Map}.
 *
 * @see AggregatedStatistics
 * @see AggregatedStatisticsJpaEntity
 */
@Component
public class StatisticsMapper {

    private static final Logger log = LoggerFactory.getLogger(StatisticsMapper.class);

    private final ObjectMapper objectMapper;

    public StatisticsMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converte un'entità persistente {@link AggregatedStatisticsJpaEntity} nel
     * corrispondente modello di dominio {@link AggregatedStatistics}.
     * <p>
     * La colonna JSON {@code data} viene deserializzata in una {@link Map};
     * se {@code null} o vuota viene restituita una mappa vuota.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link AggregatedStatistics} o {@code null} se l'entità è {@code null}
     * @throws RuntimeException se la colonna {@code data} contiene un JSON non valido
     * @see #toEntity(AggregatedStatistics)
     */
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
                entity.getTotalAbortedSessions(),
                dataMap
        );
    }

    /**
     * Converte un modello di dominio {@link AggregatedStatistics} nell'entità
     * persistente {@link AggregatedStatisticsJpaEntity} da persistere.
     * <p>
     * La mappa {@code data} viene serializzata in una stringa JSON; se
     * {@code null} viene serializzata come {@code "{}"}.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link AggregatedStatisticsJpaEntity} o {@code null} se il dominio è {@code null}
     * @throws RuntimeException se la mappa {@code data} non può essere serializzata in JSON
     * @see #toDomain(AggregatedStatisticsJpaEntity)
     */
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
                domain.getTotalAbortedSessions(),
                dataStr
        );
    }
}
