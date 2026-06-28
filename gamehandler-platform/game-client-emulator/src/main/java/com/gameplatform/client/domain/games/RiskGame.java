package com.gameplatform.client.domain.games;

import com.gameplatform.client.domain.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RiskGame implements GameLifecycle {
    private List<UserId> participants;
    private StopReason stopReason;
    private Map<UserId, Map<String, Integer>> resources;
    private int turnIndex;
    private boolean running;
    private GameSessionId sessionId;

    public RiskGame(GameSessionId sessionId) {
        this.resources = new HashMap<>();
        this.turnIndex = 0;
        this.stopReason = null;
        this.running = false;
        this.participants = new ArrayList<>();
        this.sessionId = sessionId;
    }

    public List<UserId> getParticipants() {
        return participants;
    }

    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public Map<UserId, Map<String, Integer>> getResources() {
        return resources;
    }

    public void setResources(Map<UserId, Map<String, Integer>> resources) {
        this.resources = resources;
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    public void setRunning(boolean running) { this.running = running; }

    public GameSessionId getSessionId() {
        return sessionId;
    }

    @Override
    public void start(List<UserId> participants) {
        this.participants = participants;
        this.running = true;
        this.stopReason = null;
        this.resources.clear();
        for (UserId participant : participants) {
            Map<String, Integer> playerResources = new HashMap<>();
            playerResources.put("armies", 5);
            playerResources.put("territories", 0);
            resources.put(participant, playerResources);
        }

        this.turnIndex = 0;
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

    public void updateResources(UserId player, String key, int val) {
        if (!this.running) {
            throw new IllegalStateException("RiskGame is not running");
        }
        if (!this.resources.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerResources = this.resources.get(player);
        int newValue = playerResources.getOrDefault(key, 0) + val;
        playerResources.put(key, newValue);
    }

    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("RiskGame is not running");
        }

        this.turnIndex = (this.turnIndex + 1) % participants.size();
    }
}
