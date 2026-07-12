package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain read-model representing a single match played by a user, projected
 * from a {@code GAME_SESSION_COMPLETED} sync event by the Central
 * {@code SyncEventProcessor} (FASE 3, PIANO &sect;2.4).
 *
 * <p>One row per (session, participant): a session with N participants yields
 * N {@code PlayerMatchFact}s. Identity is the composite (sessionId, userId)
 * pair, mirroring the {@code player_match_facts} table primary key.</p>
 *
 * <p>Pure Java (no framework annotations), following the
 * {@code GameDefinition}/{@code LocalAdminBuilding} POJO convention. The
 * {@code tournamentId} column is nullable and left {@code null} in FASE 3;
 * FASE 6 will populate it when sessions are tied to tournament matches.</p>
 */
public class PlayerMatchFact {
    private final String sessionId;
    private final UserId userId;
    private final BuildingId buildingId;
    private final GameType gameType;
    private final String tournamentId;
    private final boolean won;
    private final WinCondition winCondition;
    private final Instant endedAt;

    public PlayerMatchFact(String sessionId,
                           UserId userId,
                           BuildingId buildingId,
                           GameType gameType,
                           String tournamentId,
                           boolean won,
                           WinCondition winCondition,
                           Instant endedAt) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be null or blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt cannot be null");
        }
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

    public UserId getUserId() {
        return userId;
    }

    public BuildingId getBuildingId() {
        return buildingId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public boolean isWon() {
        return won;
    }

    public WinCondition getWinCondition() {
        return winCondition;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerMatchFact that = (PlayerMatchFact) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId);
    }
}