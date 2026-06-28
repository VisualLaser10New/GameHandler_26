package com.gameplatform.client.domain.games;

import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DartsGame {
    private List<UserId> participants;
    private Map<UserId, Integer> scores;
    private boolean running;
    private int turnIndex;
    private StopReason stopReason;

    public DartsGame() {
        participants = new ArrayList<>();
        scores = new HashMap<>();
        running = false;
        turnIndex = 0;
    }

    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.scores.clear();
        for (UserId userId : participants) {
            this.scores.put(userId, 0);
        }
        this.stopReason = null;
    }

    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
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
