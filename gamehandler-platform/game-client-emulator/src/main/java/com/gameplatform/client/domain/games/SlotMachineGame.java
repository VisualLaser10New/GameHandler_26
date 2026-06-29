package com.gameplatform.client.domain.games;

import com.gameplatform.client.domain.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.*;

public class SlotMachineGame implements GameLifecycle {
    private List<UserId> participants;
    private StopReason stopReason;
    private Map<UserId, Integer> scores;
    private boolean running;
    private GameSessionId sessionId;

    public SlotMachineGame(GameSessionId sessionId) {
        this.participants = new ArrayList<UserId>();
        this.stopReason = null;
        this.scores = new HashMap<UserId, Integer>();
        this.running = false;
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

    public Map<UserId, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<UserId, Integer> scores) {
        this.scores = scores;
    }

    public void setRunning(boolean running) { this.running = running; }

    public GameSessionId getSessionId() {
        return sessionId;
    }

    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
        this.scores.clear();
        for (UserId participant : participants) {
            this.scores.put(participant, 0);
        }
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

    public void recordScore(UserId player, int score) {
        if (!this.running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!this.scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }
        this.scores.put(player, score);
    }

    private static final String[] SYMBOLS = {"CHERRY", "LEMON", "ORANGE", "PLUM", "BELL", "SEVEN"};
    private static final Random RANDOM = new Random();

    private String lastReel1;
    private String lastReel2;
    private String lastReel3;

    public void spin(UserId player) {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }

        lastReel1 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        lastReel2 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        lastReel3 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];

        int points = calculatePayout(lastReel1, lastReel2, lastReel3);
        scores.put(player, scores.get(player) + points);
    }

    private int calculatePayout(String r1, String r2, String r3) {
        if (r1.equals(r2) && r2.equals(r3)) {
            return 100;
        }
        if (r1.equals(r2) || r2.equals(r3) || r1.equals(r3)) {
            return 10;
        }
        return 0;
    }

    public String getLastReel1() { return lastReel1; }
    public String getLastReel2() { return lastReel2; }
    public String getLastReel3() { return lastReel3; }
}
