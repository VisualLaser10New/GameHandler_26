package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
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
    private List<UserId> participants;

    // Pause tracking: when the session was paused, and how many seconds
    // of pause have accumulated across all pause/resume cycles.
    private Instant pausedAt;
    private int accumulatedPausedSeconds;

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
        this.participants = participants != null ? List.copyOf(participants) : List.of();
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
        if (this.status == GameStatus.COMPLETED) {
            throw new InvalidGameStateTransitionException("Cannot complete session because it is already completed");
        }
        if (this.status != GameStatus.IN_PROGRESS && this.status != GameStatus.PAUSED && this.status != GameStatus.ABORTED) {
            throw new InvalidGameStateTransitionException("Cannot complete session because its current status is: " + this.status);
        }
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
        if (this.status == GameStatus.ABORTED || this.status == GameStatus.COMPLETED) {
            throw new InvalidGameStateTransitionException("Cannot abort session because it is already " + this.status);
        }
        if (this.status != GameStatus.IN_PROGRESS && this.status != GameStatus.PAUSED) {
            throw new InvalidGameStateTransitionException("Cannot abort session because its current status is: " + this.status);
        }
        this.status = GameStatus.ABORTED;
        this.endedAt = endedAt;
        if (reason == StopReason.TIMEOUT) {
            this.winCondition = WinCondition.TIMEOUT;
        } else {
            this.winCondition = WinCondition.ABANDONED;
        }
        calculateDuration();
    }

    public void cancelLobby(Instant endedAt) {
        if (this.status != GameStatus.WAITING) {
            throw new InvalidGameStateTransitionException("Cannot cancel lobby because its current status is: " + this.status);
        }
        this.status = GameStatus.ABORTED;
        this.endedAt = endedAt;
        this.winCondition = WinCondition.TIMEOUT;
        calculateDuration();
    }

    public void pause() {
        pause(Instant.now());
    }

    public void pause(Instant pausedAt) {
        if (this.status != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStateTransitionException("Cannot pause session because its current status is: " + this.status);
        }
        this.status = GameStatus.PAUSED;
        this.pausedAt = pausedAt;
    }

    public void resume() {
        resume(Instant.now());
    }

    public void resume(Instant resumedAt) {
        if (this.status != GameStatus.PAUSED) {
            throw new InvalidGameStateTransitionException("Cannot resume session because its current status is: " + this.status);
        }
        this.status = GameStatus.IN_PROGRESS;
        if (this.pausedAt != null) {
            this.accumulatedPausedSeconds += (int) Duration.between(this.pausedAt, resumedAt).toSeconds();
            this.pausedAt = null;
        }
    }

    public void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            int totalSeconds = (int) Duration.between(startedAt, endedAt).toSeconds();
            // Subtract time spent in pause to get the effective play duration.
            int pausedSeconds = accumulatedPausedSeconds;
            if (pausedAt != null && !endedAt.isBefore(pausedAt)) {
                // Session ended while paused — include the final pause interval.
                pausedSeconds += (int) Duration.between(pausedAt, endedAt).toSeconds();
            }
            this.durationSeconds = Math.max(0, totalSeconds - pausedSeconds);
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

    public Instant getPausedAt() {
        return pausedAt;
    }

    public int getAccumulatedPausedSeconds() {
        return accumulatedPausedSeconds;
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
        return List.copyOf(participants);
    }

    public void addParticipant(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (this.participants.contains(userId)) {
            return; // Already in participants
        }
        List<UserId> newList = new ArrayList<>(this.participants);
        newList.add(userId);
        this.participants = List.copyOf(newList);
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }
}

