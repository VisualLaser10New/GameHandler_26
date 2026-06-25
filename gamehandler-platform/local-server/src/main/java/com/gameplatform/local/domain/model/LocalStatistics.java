package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalStatistics {
    private final GameType gameType;
    private int totalSessions;
    private double avgDuration;
    private int totalReservations;
    private Map<String, Double> winRateByUser;

    public LocalStatistics(GameType gameType, int totalSessions, double avgDuration, int totalReservations, Map<String, Double> winRateByUser) {
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        this.gameType = gameType;
        this.totalSessions = totalSessions;
        this.avgDuration = avgDuration;
        this.totalReservations = totalReservations;
        this.winRateByUser = winRateByUser != null ? Map.copyOf(winRateByUser) : new HashMap<>();
    }

    public void recalculate(List<GameSession> sessions) {
        if (sessions == null) {
            throw new IllegalArgumentException("Sessions list cannot be null");
        }

        List<GameSession> filteredSessions = sessions.stream()
                .filter(s -> s.getGameType() == this.gameType)
                .toList();

        this.totalSessions = filteredSessions.size();

        this.avgDuration = filteredSessions.stream()
                .filter(s -> s.getStatus() == GameStatus.COMPLETED && s.getDurationSeconds() != null)
                .mapToInt(GameSession::getDurationSeconds)
                .average()
                .orElse(0.0);

        Map<String, Integer> participations = new HashMap<>();
        Map<String, Integer> wins = new HashMap<>();

        for (GameSession session : filteredSessions) {
            if (session.getParticipants() != null) {
                for (UserId participant : session.getParticipants()) {
                    String userVal = participant.value();
                    participations.put(userVal, participations.getOrDefault(userVal, 0) + 1);

                    if (session.getStatus() == GameStatus.COMPLETED && session.getWinnerId() != null
                            && session.getWinnerId().equals(participant)) {
                        wins.put(userVal, wins.getOrDefault(userVal, 0) + 1);
                    }
                }
            }
        }

        Map<String, Double> rates = new HashMap<>();
        for (String userVal : participations.keySet()) {
            int total = participations.get(userVal);
            int winCount = wins.getOrDefault(userVal, 0);
            rates.put(userVal, (double) winCount / total);
        }
        this.winRateByUser = rates;
    }

    public void setTotalReservations(int totalReservations) {
        this.totalReservations = totalReservations;
    }

    public GameType getGameType() {
        return gameType;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public double getAvgDuration() {
        return avgDuration;
    }

    public int getTotalReservations() {
        return totalReservations;
    }

    public Map<String, Double> getWinRateByUser() {
        return winRateByUser;
    }
}

