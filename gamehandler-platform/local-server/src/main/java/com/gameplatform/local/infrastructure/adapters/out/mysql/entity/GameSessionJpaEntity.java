package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_sessions")
public class GameSessionJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "building_id", nullable = false, length = 36)
    private String buildingId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_s")
    private Integer durationSeconds;

    @Column(name = "winner_id", length = 36)
    private String winnerId;

    @Column(name = "win_condition", length = 30)
    private String winCondition;

    @Column(name = "result_data", columnDefinition = "JSON")
    private String resultData;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id")
    private List<SessionParticipantJpaEntity> participants = new ArrayList<>();

    public GameSessionJpaEntity() {
    }

    public GameSessionJpaEntity(String id, String gameId, String gameType, String buildingId, String status, Instant startedAt, Instant endedAt, Integer durationSeconds, String winnerId, String winCondition, String resultData, List<SessionParticipantJpaEntity> participants) {
        this.id = id;
        this.gameId = gameId;
        this.gameType = gameType;
        this.buildingId = buildingId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.winnerId = winnerId;
        this.winCondition = winCondition;
        this.resultData = resultData;
        this.participants = participants != null ? participants : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinCondition() {
        return winCondition;
    }

    public void setWinCondition(String winCondition) {
        this.winCondition = winCondition;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public List<SessionParticipantJpaEntity> getParticipants() {
        return participants;
    }

    public void setParticipants(List<SessionParticipantJpaEntity> participants) {
        this.participants = participants;
    }
}
