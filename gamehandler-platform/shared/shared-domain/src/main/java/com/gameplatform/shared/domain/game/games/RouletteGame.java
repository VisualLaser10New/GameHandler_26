package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.*;

public class RouletteGame implements GameLifecycle {
    private List<UserId> participants;
    private boolean running;
    private StopReason stopReason;
    private Map<UserId, Map<String, Integer>> bets;
    private int turnIndex;
    private GameSessionId sessionId;

    public RouletteGame(GameSessionId sessionId) {
        this.participants = new ArrayList<>();
        this.running = false;
        this.stopReason = null;
        this.bets = new HashMap<>();
        this.turnIndex = 0;
        this.sessionId = sessionId;
    }

    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    public Map<UserId, Map<String, Integer>> getBets() {
        return bets;
    }

    public void setBets(Map<UserId, Map<String, Integer>> bets) {
        this.bets = bets;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public void setRunning(boolean running) { this.running = running; }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    @Override
    public void start(List<UserId> participants) {
        this.participants = participants;
        this.running = true;
        this.stopReason = null;
        this.bets.clear();
        this.turnIndex = 0;

        for (UserId player: participants) {
            bets.put(player, new HashMap<>());
        }
    }

    @Override
    public void stop(StopReason reason) {
        this.stopReason = reason;
        this.running = false;
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
        return GameType.ROULETTE;
    }

    @Override
    public int getMinPlayers() {
        return 1;
    }

    @Override
    public int getMaxPlayers() {
        return 20;
    }

    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("RouletteGame in not running");
        }
        turnIndex = (turnIndex + 1) % participants.size();
    }

    public void placeBet(UserId player, String num, int amount) {
        if (!this.running) {
            throw new IllegalStateException("RouletteGame in not running");
        }
        if (!participants.contains(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerBets = bets.get(player);
        int newAmount = playerBets.getOrDefault(num, 0) + amount;
        playerBets.put(num, newAmount);
    }
}
