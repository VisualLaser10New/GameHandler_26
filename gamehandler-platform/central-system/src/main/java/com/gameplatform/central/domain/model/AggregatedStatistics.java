package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

public class AggregatedStatistics {
    private String id;
    private BuildingId buildingId;
    private GameType gameType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int totalSessions;
    private int avgDurationSeconds;
    private int totalReservations;
    private Map<String, Object> data;

    public AggregatedStatistics(String id, BuildingId buildingId, GameType gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, Map<String, Object> data) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Period bounds cannot be null");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart cannot be after periodEnd");
        }
        if (totalSessions < 0) {
            throw new IllegalArgumentException("totalSessions cannot be negative");
        }
        if (avgDurationSeconds < 0) {
            throw new IllegalArgumentException("avgDurationSeconds cannot be negative");
        }
        if (totalReservations < 0) {
            throw new IllegalArgumentException("totalReservations cannot be negative");
        }
        this.id = id;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSessions = totalSessions;
        this.avgDurationSeconds = avgDurationSeconds;
        this.totalReservations = totalReservations;
        this.data = data != null ? new java.util.HashMap<>(data) : new java.util.HashMap<>();
    }

    public void mergeWith(AggregatedStatistics other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot merge with null statistics");
        }

        if (!java.util.Objects.equals(this.buildingId, other.buildingId)) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different buildings");
        }
        if (this.gameType != other.gameType) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different game types");
        }
        if (other.periodStart == null || other.periodEnd == null) {
            throw new IllegalArgumentException("Other statistics period bounds cannot be null");
        }
        if (other.periodStart.isAfter(other.periodEnd)) {
            throw new IllegalArgumentException("Other statistics periodStart cannot be after periodEnd");
        }

        int combinedTotalSessions = this.totalSessions + other.totalSessions;
        if (combinedTotalSessions > 0) {
            long totalDurationThis = (long) this.avgDurationSeconds * this.totalSessions;
            long totalDurationOther = (long) other.avgDurationSeconds * other.totalSessions;
            this.avgDurationSeconds = (int) Math.round((double) (totalDurationThis + totalDurationOther) / combinedTotalSessions);
        } else {
            this.avgDurationSeconds = 0;
        }

        this.totalSessions = combinedTotalSessions;
        this.totalReservations += other.totalReservations;

        if (this.periodStart == null || other.periodStart.isBefore(this.periodStart)) {
            this.periodStart = other.periodStart;
        }
        if (this.periodEnd == null || other.periodEnd.isAfter(this.periodEnd)) {
            this.periodEnd = other.periodEnd;
        }

        this.data = mergeDataMaps(this.data, other.data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeDataMaps(Map<String, Object> map1, Map<String, Object> map2) {
        if (map1 == null && map2 == null) {
            return new HashMap<>();
        }
        if (map1 == null) {
            return new HashMap<>(map2);
        }
        if (map2 == null) {
            return new HashMap<>(map1);
        }

        Map<String, Object> merged = new HashMap<>(map1);
        for (Map.Entry<String, Object> entry : map2.entrySet()) {
            String key = entry.getKey();
            Object val2 = entry.getValue();
            if (!merged.containsKey(key)) {
                merged.put(key, val2);
            } else {
                Object val1 = merged.get(key);
                if (val1 instanceof Map && val2 instanceof Map) {
                    merged.put(key, mergeDataMaps((Map<String, Object>) val1, (Map<String, Object>) val2));
                } else if (val1 instanceof Number && val2 instanceof Number) {
                    merged.put(key, sumNumbers((Number) val1, (Number) val2));
                } else {
                    merged.put(key, val2); // Fallback to overwrite
                }
            }
        }
        return merged;
    }

    private Number sumNumbers(Number n1, Number n2) {
        if (n1 instanceof java.math.BigDecimal || n2 instanceof java.math.BigDecimal) {
            java.math.BigDecimal bd1 = toBigDecimal(n1);
            java.math.BigDecimal bd2 = toBigDecimal(n2);
            return bd1.add(bd2);
        }
        if (n1 instanceof Double || n2 instanceof Double || n1 instanceof Float || n2 instanceof Float) {
            return n1.doubleValue() + n2.doubleValue();
        } else {
            long sum = n1.longValue() + n2.longValue();
            if (sum >= Integer.MIN_VALUE && sum <= Integer.MAX_VALUE) {
                return (int) sum;
            }
            return sum;
        }
    }

    private java.math.BigDecimal toBigDecimal(Number number) {
        if (number instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) number;
        }
        if (number instanceof java.math.BigInteger) {
            return new java.math.BigDecimal((java.math.BigInteger) number);
        }
        if (number instanceof Double || number instanceof Float) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        return java.math.BigDecimal.valueOf(number.longValue());
    }

    public String getId() {
        return id;
    }
    public BuildingId getBuildingId() {
        return buildingId;
    }
    public GameType getGameType() {
        return gameType;
    }
    public LocalDate getPeriodStart() {
        return periodStart;
    }
    public LocalDate getPeriodEnd() {
        return periodEnd;
    }
    public int getTotalSessions() {
        return totalSessions;
    }
    public int getAvgDurationSeconds() {
        return avgDurationSeconds;
    }
    public int getTotalReservations() {
        return totalReservations;
    }
    public Map<String, Object> getData() {
        return java.util.Collections.unmodifiableMap(data);
    }
}

