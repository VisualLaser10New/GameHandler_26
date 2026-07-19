package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistiche locali calcolate per un determinato tipo di gioco, includendo
 * il numero totale di sessioni, la durata media, il totale delle prenotazioni
 * e il tasso di vittoria per utente. I dati vengono ricalcolati a partire
 * dalla lista delle sessioni.
 *
 * @see GameSession
 * @see GameType
 */
public class LocalStatistics {
    private final GameType gameType;
    private int totalSessions;
    private double avgDuration;
    private int totalReservations;
    private Map<String, Double> winRateByUser;

    /**
     * Costruisce un nuovo oggetto statistiche locali.
     *
     * @param gameType          tipo di gioco (non null)
     * @param totalSessions     numero totale di sessioni
     * @param avgDuration       durata media delle sessioni in secondi
     * @param totalReservations numero totale di prenotazioni
     * @param winRateByUser     mappa dei tassi di vittoria per utente (può essere null)
     * @throws IllegalArgumentException se gameType è null
     */
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

    /**
     * Ricalcola le statistiche a partire dalla lista completa delle sessioni,
     * filtrando quelle corrispondenti al tipo di gioco e aggiornando
     * il numero di sessioni, la durata media e il tasso di vittoria per utente.
     *
     * @param sessions lista di tutte le sessioni (non null)
     * @throws IllegalArgumentException se sessions è null
     */
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

    /**
     * Imposta il numero totale di prenotazioni.
     *
     * @param totalReservations totale prenotazioni
     */
    public void setTotalReservations(int totalReservations) {
        this.totalReservations = totalReservations;
    }

    /**
     * Restituisce il tipo di gioco.
     *
     * @return gameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce il numero totale di sessioni.
     *
     * @return totalSessions
     */
    public int getTotalSessions() {
        return totalSessions;
    }

    /**
     * Restituisce la durata media delle sessioni in secondi.
     *
     * @return avgDuration
     */
    public double getAvgDuration() {
        return avgDuration;
    }

    /**
     * Restituisce il numero totale di prenotazioni.
     *
     * @return totalReservations
     */
    public int getTotalReservations() {
        return totalReservations;
    }

    /**
     * Restituisce la mappa dei tassi di vittoria per utente.
     *
     * @return winRateByUser
     */
    public Map<String, Double> getWinRateByUser() {
        return winRateByUser;
    }
}

