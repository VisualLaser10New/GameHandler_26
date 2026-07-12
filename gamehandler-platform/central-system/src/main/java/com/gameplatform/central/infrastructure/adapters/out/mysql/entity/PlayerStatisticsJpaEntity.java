package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code player_statistics} read-model table (FASE 3, PIANO
 * &sect;2.3). Aggregated per-player, per-game-type counters.
 *
 * <p>Uses a composite primary key ({@code user_id}, {@code game_type}) via
 * {@link IdClass}, matching the PIANO SQL {@code PRIMARY KEY (user_id,
 * game_type)}. No JPA relations are declared — every foreign key is a plain
 * {@code String} column, per the hexagonal convention.</p>
 */
@Entity
@Table(name = "player_statistics")
@IdClass(PlayerStatisticsId.class)
public class PlayerStatisticsJpaEntity {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Id
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "matches_played", nullable = false)
    private Integer matchesPlayed;

    @Column(name = "matches_won", nullable = false)
    private Integer matchesWon;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    public PlayerStatisticsJpaEntity() {
    }

    public PlayerStatisticsJpaEntity(String userId, String gameType, Integer matchesPlayed,
                                     Integer matchesWon, Instant lastPlayedAt) {
        this.userId = userId;
        this.gameType = gameType;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.lastPlayedAt = lastPlayedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public Integer getMatchesPlayed() {
        return matchesPlayed;
    }

    public void setMatchesPlayed(Integer matchesPlayed) {
        this.matchesPlayed = matchesPlayed;
    }

    public Integer getMatchesWon() {
        return matchesWon;
    }

    public void setMatchesWon(Integer matchesWon) {
        this.matchesWon = matchesWon;
    }

    public Instant getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(Instant lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }
}