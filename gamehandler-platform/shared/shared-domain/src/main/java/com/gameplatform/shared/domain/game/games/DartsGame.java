package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DartsGame implements GameLifecycle {
    private List<UserId> participants;
    private Map<UserId, Integer> scores;
    private boolean running;
    private int turnIndex;
    private GameSessionId sessionId;
    private StopReason stopReason;

    public DartsGame(GameSessionId sessionId) {
        participants = new ArrayList<>();
        scores = new HashMap<>();
        running = false;
        turnIndex = 0;
        this.sessionId = sessionId;
    }

    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    public Map<UserId, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<UserId, Integer> scores) {
        this.scores = scores;
    }

    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    public void setRunning(boolean running) { this.running = running; }

    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.scores.clear();
        for (UserId userId : participants) {
            this.scores.put(userId, 0);
        }
        this.stopReason = null;
    }

    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    @Override
    public void pause() {
        this.running = false;
    }

    @Override
    public void resume() {
        this.running = true;
    }

    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    @Override
    public GameType getGameType() {
        return GameType.DARTS;
    }

    @Override
    public int getMinPlayers() {
        return 2;
    }

    @Override
    public int getMaxPlayers() {
        return 3;
    }

    public void recordScore(UserId player, int delta) {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }

        int newScore = scores.get(player) + delta;
        this.scores.put(player, newScore);
    }

    public void endTurn() {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        turnIndex = (turnIndex + 1) % participants.size();
    }
}
