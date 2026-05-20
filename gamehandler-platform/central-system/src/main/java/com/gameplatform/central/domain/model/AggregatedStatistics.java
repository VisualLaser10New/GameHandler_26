package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        this.id = id;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalSessions = totalSessions;
        this.avgDurationSeconds = avgDurationSeconds;
        this.totalReservations = totalReservations;
        this.data = data;
    }

    public void mergeWith(AggregatedStatistics other) {
        if (other == null) {
            return;
        }

        if (!Objects.equals(this.buildingId, other.buildingId)) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different buildings");
        }
        if (this.gameType != other.gameType) {
            throw new IllegalArgumentException("Cannot merge statistics belonging to different game types");
        }

        int combinedTotalSessions = this.totalSessions + other.totalSessions;
        if (combinedTotalSessions > 0) {
            long totalDurationThis = (long) this.avgDurationSeconds * this.totalSessions;
            long totalDurationOther = (long) other.avgDurationSeconds * other.totalSessions;
            this.avgDurationSeconds = (int) ((totalDurationThis + totalDurationOther) / combinedTotalSessions);
        } else {
            this.avgDurationSeconds = 0;
        }

        this.totalSessions = combinedTotalSessions;
        this.totalReservations += other.totalReservations;

        if (other.periodStart != null && (this.periodStart == null || other.periodStart.isBefore(this.periodStart))) {
            this.periodStart = other.periodStart;
        }
        if (other.periodEnd != null && (this.periodEnd == null || other.periodEnd.isAfter(this.periodEnd))) {
            this.periodEnd = other.periodEnd;
        }

        Map<String, Object> mergedData = new HashMap<>();
        if (this.data != null) {
            mergedData.putAll(this.data);
        }
        if (other.data != null) {
            mergedData.putAll(other.data);
        }
        this.data = mergedData;
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
        return data;
    }
}
