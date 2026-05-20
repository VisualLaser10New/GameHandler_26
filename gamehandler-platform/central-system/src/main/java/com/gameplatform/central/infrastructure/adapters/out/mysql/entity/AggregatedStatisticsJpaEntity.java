package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "aggregated_statistics")
public class AggregatedStatisticsJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_sessions", nullable = false)
    private int totalSessions;

    @Column(name = "avg_duration_seconds", nullable = false)
    private int avgDurationSeconds;

    @Column(name = "total_reservations", nullable = false)
    private int totalReservations;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    public AggregatedStatisticsJpaEntity() {
    }

    public AggregatedStatisticsJpaEntity(String id, String buildingId, String gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, String data) {
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getAvgDurationSeconds() {
        return avgDurationSeconds;
    }

    public void setAvgDurationSeconds(int avgDurationSeconds) {
        this.avgDurationSeconds = avgDurationSeconds;
    }

    public int getTotalReservations() {
        return totalReservations;
    }

    public void setTotalReservations(int totalReservations) {
        this.totalReservations = totalReservations;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
