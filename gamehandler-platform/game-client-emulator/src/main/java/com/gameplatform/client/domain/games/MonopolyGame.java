package com.gameplatform.client.domain.games;

import com.gameplatform.client.domain.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonopolyGame implements GameLifecycle {
    private List<UserId> partecipants;
    private boolean running;
    private GameSessionId sessionId;
    private Map<UserId, Map<String, Integer>> resources;
    private int turnIndex;
    private StopReason stopReason;

    public MonopolyGame(GameSessionId sessionId) {
        partecipants = new ArrayList<>();
        running = false;
        this.resources = new HashMap<>();
        this.turnIndex = 0;
        this.stopReason = null;
        this.sessionId = sessionId;
    }

    public GameSessionId getSessionId() {
        return sessionId;
    }

    public List<UserId> getPartecipants() {
        return partecipants;
    }

    public void setPartecipants(List<UserId> partecipants) {
        this.partecipants = partecipants;
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

    public StopReason getStopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    @Override
    public void start(List<UserId> participants) {
        running = true;
        this.partecipants = participants;
        this.stopReason = null;

        this.resources.clear();
        for (UserId participant : participants) {
            Map<String, Integer> initial = new HashMap<>();
            initial.put("money", 1500);
            this.resources.put(participant, initial);
        }

        this.turnIndex = 0;
    }

    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    public void updateResource(UserId player, String key, int val) {
        if (!running) {
            throw new IllegalStateException("MonopolyGame is not running");
        }
        if (!resources.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerResources = resources.get(player);
        int newValue = playerResources.getOrDefault(key, 0) + val;
        playerResources.put(key, newValue);
    }

    public void endTurn() {
        if (!running) {
            throw new IllegalStateException("MonopolyGame is not running");
        }

        this.turnIndex = (this.turnIndex + 1) % partecipants.size();
    }
}
