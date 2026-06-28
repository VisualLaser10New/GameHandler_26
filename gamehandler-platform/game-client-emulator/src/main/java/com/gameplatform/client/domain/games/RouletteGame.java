package com.gameplatform.client.domain.games;

import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.*;

public class RouletteGame {
    private List<UserId> participants;
    private boolean running;
    private StopReason stopReason;
    private Map<UserId, Map<String, Integer>> bets;
    private int turnIndex;

    public RouletteGame() {
        this.participants = new ArrayList<>();
        this.running = false;
        this.stopReason = null;
        this.bets = new HashMap<>();
        this.turnIndex = 0;
    }

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

    public void stop(StopReason reason) {
        this.stopReason = reason;
        this.running = false;
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
