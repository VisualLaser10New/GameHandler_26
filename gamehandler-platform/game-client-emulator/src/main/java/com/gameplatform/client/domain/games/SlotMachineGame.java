package com.gameplatform.client.domain.games;

import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlotMachineGame {
    private List<UserId> participants;
    private StopReason stopReason;
    private Map<UserId, Integer> scores;
    private boolean running;

    public SlotMachineGame() {
        this.participants = new ArrayList<UserId>();
        this.stopReason = null;
        this.scores = new HashMap<UserId, Integer>();
        this.running = false;
    }

    public void start(List<UserId> participants, StopReason stopReason) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
        this.scores.clear();
        for (UserId participant : participants) {
            this.scores.put(participant, 0);
        }
    }

    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    public void recordScore(UserId player, int score) {
        if (!this.running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!this.scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }
        this.scores.put(player, score);
    }

    public void spin() {
        // TODO
    }
}
