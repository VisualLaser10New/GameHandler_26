package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain read-model aggregating a single player's match history per game type,
 * stored in the Central {@code player_statistics} table (FASE 3, PIANO
 * &sect;2.3). Identity is the composite (userId, gameType) pair.
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code LocalAdminBuilding} POJO convention.
 * {@link #mergeIncrement(boolean, Instant)} returns a NEW immutable instance
 * with the counters incremented and {@code lastPlayedAt} advanced to the
 * later of the two instants; the {@code SyncEventProcessor} projection relies
 * on it to perform atomic increments under a pessimistic write lock.</p>
 */
public class PlayerStatistics {
    private final UserId userId;
    private final GameType gameType;
    private final int matchesPlayed;
    private final int matchesWon;
    private final Instant lastPlayedAt;

    public PlayerStatistics(UserId userId, GameType gameType, int matchesPlayed, int matchesWon, Instant lastPlayedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (matchesPlayed < 0) {
            throw new IllegalArgumentException("matchesPlayed cannot be negative");
        }
        if (matchesWon < 0) {
            throw new IllegalArgumentException("matchesWon cannot be negative");
        }
        if (matchesWon > matchesPlayed) {
            throw new IllegalArgumentException("matchesWon cannot exceed matchesPlayed");
        }
        this.userId = userId;
        this.gameType = gameType;
        this.matchesPlayed = matchesPlayed;
        this.matchesWon = matchesWon;
        this.lastPlayedAt = lastPlayedAt;
    }

    /**
     * Returns a new {@code PlayerStatistics} representing this plus one
     * additional match. {@code lastPlayedAt} becomes the later of the existing
     * value and {@code endedAt} (or {@code endedAt} when the existing value is
     * null). The receiver is left unchanged (immutable merge).
     *
     * @param won     whether the player won the additional match
     * @param endedAt when the additional match ended (must not be null)
     */
    public PlayerStatistics mergeIncrement(boolean won, Instant endedAt) {
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt cannot be null");
        }
        Instant newLastPlayedAt = (lastPlayedAt == null || endedAt.isAfter(lastPlayedAt)) ? endedAt : lastPlayedAt;
        return new PlayerStatistics(
                userId,
                gameType,
                matchesPlayed + 1,
                matchesWon + (won ? 1 : 0),
                newLastPlayedAt);
    }

    public UserId getUserId() {
        return userId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public int getMatchesWon() {
        return matchesWon;
    }

    public Instant getLastPlayedAt() {
        return lastPlayedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStatistics that = (PlayerStatistics) o;
        return Objects.equals(userId, that.userId) && Objects.equals(gameType, that.gameType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, gameType);
    }
}