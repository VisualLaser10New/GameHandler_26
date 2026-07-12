package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code player_match_facts} read-model table (FASE 3,
 * PIANO &sect;2.3). One row per (session, participant).
 *
 * <p>Uses a composite primary key ({@code session_id}, {@code user_id}) via
 * {@link IdClass} so the JPA mapping matches the PIANO SQL
 * {@code PRIMARY KEY (session_id, user_id)} exactly (precedent:
 * {@code LocalAdminBuildingJpaEntity}, {@code SessionParticipantJpaEntity}). No
 * JPA relations are declared — every foreign key is a plain {@code String}
 * column, per the hexagonal convention used throughout the project.</p>
 */
@Entity
@Table(name = "player_match_facts")
@IdClass(PlayerMatchFactId.class)
public class PlayerMatchFactJpaEntity {

    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "tournament_id", length = 36)
    private String tournamentId;

    @Column(name = "won", nullable = false)
    private Boolean won;

    @Column(name = "win_condition", length = 30)
    private String winCondition;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    public PlayerMatchFactJpaEntity() {
    }

    public PlayerMatchFactJpaEntity(String sessionId, String userId, String buildingId, String gameType,
                                    String tournamentId, Boolean won, String winCondition, Instant endedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.buildingId = buildingId;
        this.gameType = gameType;
        this.tournamentId = tournamentId;
        this.won = won;
        this.winCondition = winCondition;
        this.endedAt = endedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Boolean getWon() {
        return won;
    }

    public void setWon(Boolean won) {
        this.won = won;
    }

    public String getWinCondition() {
        return winCondition;
    }

    public void setWinCondition(String winCondition) {
        this.winCondition = winCondition;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }
}