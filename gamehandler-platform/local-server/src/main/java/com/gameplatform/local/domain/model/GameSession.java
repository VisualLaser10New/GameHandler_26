package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.GameResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GameSession {
    private final GameSessionId id;
    private final GameId gameId;
    private final GameType gameType;
    private final BuildingId buildingId;
    private GameStatus status;
    private final Instant startedAt;
    private Instant endedAt;
    private Integer durationSeconds;
    private UserId winnerId;
    private WinCondition winCondition;
    private GameResult result;
    private final List<UserId> participants;

    // Costruttore con partecipanti
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result, List<UserId> participants) {
        if (id == null) {
            throw new IllegalArgumentException("GameSessionId cannot be null");
        }
        if (gameId == null) {
            throw new IllegalArgumentException("GameId cannot be null");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (buildingId == null) {
            throw new IllegalArgumentException("BuildingId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("GameStatus cannot be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("StartedAt cannot be null");
        }
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
        this.result = result;
        this.participants = participants != null ? List.copyOf(participants) : new ArrayList<>();
    }

    // Costruttore per compatibilità esatta con workflow.md (senza partecipanti)
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status,
                       Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId,
                       WinCondition winCondition, GameResult result) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds, winnerId, winCondition, result, new ArrayList<>());
    }

    public void complete(GameResult result) {
        complete(result, Instant.now());
    }

    public void complete(GameResult result, Instant endedAt) {
        this.status = GameStatus.COMPLETED;
        this.result = result;
        this.endedAt = endedAt;
        if (result != null) {
            this.winnerId = result.getWinnerId();
            this.winCondition = result.getWinCondition();
        }
        calculateDuration();
    }

    public void abort(StopReason reason) {
        abort(reason, Instant.now());
    }

    public void abort(StopReason reason, Instant endedAt) {
        this.status = GameStatus.ABORTED;
        this.endedAt = endedAt;
        if (reason == StopReason.TIMEOUT) {
            this.winCondition = WinCondition.TIMEOUT;
        } else {
            this.winCondition = WinCondition.ABANDONED;
        }
        calculateDuration();
    }

    public void pause() {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot pause session because its current status is: " + this.status);
        }
        this.status = GameStatus.PAUSED;
    }

    public void resume() {
        if (this.status != GameStatus.PAUSED) {
            throw new IllegalStateException("Cannot resume session because its current status is: " + this.status);
        }
        this.status = GameStatus.IN_PROGRESS;
    }

    public void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            this.durationSeconds = (int) Duration.between(startedAt, endedAt).toSeconds();
        }
    }

    public GameSessionId getId() {
        return id;
    }

    public GameId getGameId() {
        return gameId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public BuildingId getBuildingId() {
        return buildingId;
    }

    public GameStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public UserId getWinnerId() {
        return winnerId;
    }

    public WinCondition getWinCondition() {
        return winCondition;
    }

    public GameResult getResult() {
        return result;
    }

    public List<UserId> getParticipants() {
        return participants;
    }
}

