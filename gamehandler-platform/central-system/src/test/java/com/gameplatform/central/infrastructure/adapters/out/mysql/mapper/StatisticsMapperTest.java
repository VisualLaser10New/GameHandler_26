package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsMapperTest {

    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @Test
    void shouldMapToDomainSuccessfully() {
        StatisticsMapper mapper = new StatisticsMapper(realObjectMapper);
        AggregatedStatisticsJpaEntity entity = new AggregatedStatisticsJpaEntity(
                "id-123",
                "building-456",
                "CHESS",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 7),
                10,
                300,
                5,
                "{\"key\":\"value\"}"
        );

        AggregatedStatistics domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo("id-123");
        assertThat(domain.getBuildingId().id()).isEqualTo("building-456");
        assertThat(domain.getGameType()).isEqualTo(GameType.CHESS);
        assertThat(domain.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(domain.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(domain.getTotalSessions()).isEqualTo(10);
        assertThat(domain.getAvgDurationSeconds()).isEqualTo(300);
        assertThat(domain.getTotalReservations()).isEqualTo(5);
        assertThat(domain.getData()).containsEntry("key", "value");
    }

    @Test
    void shouldMapToEntitySuccessfully() {
        StatisticsMapper mapper = new StatisticsMapper(realObjectMapper);
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        AggregatedStatistics domain = new AggregatedStatistics(
                "id-123",
                new BuildingId("building-456"),
                GameType.CHESS,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 7),
                10,
                300,
                5,
                data
        );

        AggregatedStatisticsJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo("id-123");
        assertThat(entity.getBuildingId()).isEqualTo("building-456");
        assertThat(entity.getGameType()).isEqualTo("CHESS");
        assertThat(entity.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(entity.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(entity.getTotalSessions()).isEqualTo(10);
        assertThat(entity.getAvgDurationSeconds()).isEqualTo(300);
        assertThat(entity.getTotalReservations()).isEqualTo(5);
        assertThat(entity.getData()).contains("key").contains("value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowExceptionWhenDeserializationFails() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.readValue(any(String.class), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    throw new IOException("Deserialization error");
                });

        StatisticsMapper mapper = new StatisticsMapper(mockMapper);
        AggregatedStatisticsJpaEntity entity = new AggregatedStatisticsJpaEntity(
                "id-123", "bld", "CHESS", LocalDate.now(), LocalDate.now(), 1, 1, 1, "invalid-json"
        );

        assertThatThrownBy(() -> mapper.toDomain(entity))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize AggregatedStatistics data")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void shouldThrowExceptionWhenSerializationFails() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        StatisticsMapper mapper = new StatisticsMapper(mockMapper);
        Map<String, Object> data = Map.of("key", "value");
        AggregatedStatistics domain = new AggregatedStatistics(
                "id-123", new BuildingId("bld"), GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, 1, data
        );

        assertThatThrownBy(() -> mapper.toEntity(domain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize AggregatedStatistics data")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }
}
