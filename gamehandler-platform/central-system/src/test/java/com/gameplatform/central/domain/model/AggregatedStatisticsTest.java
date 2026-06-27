package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregatedStatisticsTest {

    @Test
    void shouldCreateAggregatedStatisticsSuccessfullyWhenInputsAreValid() {
        BuildingId buildingId = new BuildingId("bld-1");
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now();
        Map<String, Object> data = Map.of("key1", "val1");

        AggregatedStatistics stats = new AggregatedStatistics(
                "stats-1", buildingId, GameType.CHESS, start, end, 10, 300, 5, data
        );

        assertThat(stats.getId()).isEqualTo("stats-1");
        assertThat(stats.getBuildingId()).isEqualTo(buildingId);
        assertThat(stats.getGameType()).isEqualTo(GameType.CHESS);
        assertThat(stats.getPeriodStart()).isEqualTo(start);
        assertThat(stats.getPeriodEnd()).isEqualTo(end);
        assertThat(stats.getTotalSessions()).isEqualTo(10);
        assertThat(stats.getAvgDurationSeconds()).isEqualTo(300);
        assertThat(stats.getTotalReservations()).isEqualTo(5);
        assertThat(stats.getData()).containsEntry("key1", "val1");
    }

    @Test
    void shouldThrowExceptionWhenConstructorInputsAreInvalid() {
        BuildingId buildingId = new BuildingId("bld-1");
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().minusDays(1); // Invalid: start after end

        assertThatThrownBy(() -> new AggregatedStatistics(
                null, buildingId, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", null, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, null, LocalDate.now(), LocalDate.now(), 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, null, LocalDate.now(), 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, LocalDate.now(), null, 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, LocalDate.now(), LocalDate.now().minusDays(1), 1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, LocalDate.now(), LocalDate.now(), -1, 1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, -1, 1, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AggregatedStatistics(
                "id", buildingId, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, -1, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenMergingIncompatibleStatistics() {
        BuildingId b1 = new BuildingId("bld-1");
        BuildingId b2 = new BuildingId("bld-2");

        AggregatedStatistics s1 = new AggregatedStatistics("s1", b1, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, 1, null);
        AggregatedStatistics s2 = new AggregatedStatistics("s2", b2, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 1, 1, null);
        AggregatedStatistics s3 = new AggregatedStatistics("s3", b1, GameType.FOOSBALL, LocalDate.now(), LocalDate.now(), 1, 1, 1, null);

        assertThatThrownBy(() -> s1.mergeWith(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> s1.mergeWith(s2))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> s1.mergeWith(s3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCorrectlyRoundAverageDurationToAvoidPrecisionLoss() {
        BuildingId b1 = new BuildingId("bld-1");
        LocalDate day1 = LocalDate.now().minusDays(1);
        LocalDate day2 = LocalDate.now();

        // Round up case: 504 / 5 = 100.8 -> rounds to 101
        AggregatedStatistics s1 = new AggregatedStatistics("s1", b1, GameType.CHESS, day1, day2, 3, 100, 1, null);
        AggregatedStatistics s2 = new AggregatedStatistics("s2", b1, GameType.CHESS, day1, day2, 2, 102, 1, null);

        s1.mergeWith(s2);
        assertThat(s1.getAvgDurationSeconds()).isEqualTo(101);
        assertThat(s1.getTotalSessions()).isEqualTo(5);
        assertThat(s1.getTotalReservations()).isEqualTo(2);

        // Round down case: 502 / 5 = 100.4 -> rounds to 100
        AggregatedStatistics s3 = new AggregatedStatistics("s3", b1, GameType.CHESS, day1, day2, 3, 100, 1, null);
        AggregatedStatistics s4 = new AggregatedStatistics("s4", b1, GameType.CHESS, day1, day2, 2, 101, 1, null);

        s3.mergeWith(s4);
        assertThat(s3.getAvgDurationSeconds()).isEqualTo(100);
    }

    @Test
    void shouldExpandPeriodBoundsOnMerge() {
        BuildingId b1 = new BuildingId("bld-1");
        LocalDate day1 = LocalDate.now().minusDays(5);
        LocalDate day2 = LocalDate.now().minusDays(3);
        LocalDate day3 = LocalDate.now().minusDays(4);
        LocalDate day4 = LocalDate.now().minusDays(1);

        AggregatedStatistics s1 = new AggregatedStatistics("s1", b1, GameType.CHESS, day3, day2, 1, 100, 1, null);
        AggregatedStatistics s2 = new AggregatedStatistics("s2", b1, GameType.CHESS, day1, day4, 1, 100, 1, null);

        s1.mergeWith(s2);
        assertThat(s1.getPeriodStart()).isEqualTo(day1);
        assertThat(s1.getPeriodEnd()).isEqualTo(day4);
    }

    @Test
    void shouldRecursivelyMergeDataMapsSummingNumbersAndOverwritingOthers() {
        BuildingId b1 = new BuildingId("bld-1");

        // Set up Map 1
        Map<String, Object> innerMap1 = new HashMap<>();
        innerMap1.put("counter", 10);
        innerMap1.put("doubleVal", 1.5);
        innerMap1.put("text", "hello");

        Map<String, Object> map1 = new HashMap<>();
        map1.put("nested", innerMap1);
        map1.put("simpleNum", 5);
        map1.put("uniquekey1", "foo");

        // Set up Map 2
        Map<String, Object> innerMap2 = new HashMap<>();
        innerMap2.put("counter", 5);
        innerMap2.put("doubleVal", 2.2);
        innerMap2.put("text", "world"); // Overwrites "hello"

        Map<String, Object> map2 = new HashMap<>();
        map2.put("nested", innerMap2);
        map2.put("simpleNum", 3.5); // Summed: 5 + 3.5 = 8.5
        map2.put("uniquekey2", "bar");

        AggregatedStatistics s1 = new AggregatedStatistics("s1", b1, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 100, 1, map1);
        AggregatedStatistics s2 = new AggregatedStatistics("s2", b1, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 100, 1, map2);

        s1.mergeWith(s2);

        Map<String, Object> merged = s1.getData();
        assertThat(merged).containsKey("uniquekey1");
        assertThat(merged).containsKey("uniquekey2");
        assertThat(merged.get("uniquekey1")).isEqualTo("foo");
        assertThat(merged.get("uniquekey2")).isEqualTo("bar");

        // simpleNum: 5 + 3.5 = 8.5
        assertThat(((Number) merged.get("simpleNum")).doubleValue()).isEqualTo(8.5);

        // nested counter: 10 + 5 = 15
        Map<String, Object> nested = (Map<String, Object>) merged.get("nested");
        assertThat(nested.get("counter")).isEqualTo(15);

        // nested doubleVal: 1.5 + 2.2 = 3.7
        assertThat(((Number) nested.get("doubleVal")).doubleValue()).isEqualTo(3.7);

        // nested text: overwritten by "world"
        assertThat(nested.get("text")).isEqualTo("world");
    }

    @Test
    void shouldReturnUnmodifiableDataMap() {
        BuildingId b1 = new BuildingId("bld-1");
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        AggregatedStatistics stats = new AggregatedStatistics("s1", b1, GameType.CHESS, LocalDate.now(), LocalDate.now(), 1, 100, 1, map);

        assertThatThrownBy(() -> stats.getData().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
