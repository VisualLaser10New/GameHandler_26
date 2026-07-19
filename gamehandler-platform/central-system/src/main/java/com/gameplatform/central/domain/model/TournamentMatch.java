package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta una singola partita all'interno del
 * tabellone di un torneo, con l'indicazione del round, della posizione nel
 * tabellone, dei partecipanti coinvolti e dei dati di svolgimento ed esito.
 * L'identità è determinata dall'identificativo della partita; diversi campi
 * sono opzionali e vengono valorizzati progressivamente durante il ciclo di
 * vita della partita.
 *
 * @see TournamentMatchId
 * @see TournamentId
 * @see TournamentMatchStatus
 * @see Tournament
 */
public class TournamentMatch {
    private final TournamentMatchId matchId;
    private final TournamentId tournamentId;
    private final int round;
    private final int bracketPosition;
    private final String participantA;
    private final String participantB;
    private final String buildingId;
    private final String gameId;
    private final String sessionId;
    private final String winner;
    private final TournamentMatchStatus status;
    private final Instant scheduledAt;
    private final Instant playedAt;
    private final String resultData;

    /**
     * Costruisce una partita di torneo con i valori specificati.
     *
     * @param matchId identificativo univoco della partita; non può essere {@code null}
     * @param tournamentId identificativo del torneo a cui la partita appartiene; non può essere {@code null}
     * @param round numero del round nel tabellone; deve essere maggiore o uguale a 1
     * @param bracketPosition posizione della partita all'interno del round; deve essere maggiore o uguale a 1
     * @param participantA identificativo del primo partecipante; non può essere {@code null} né vuoto
     * @param participantB identificativo del secondo partecipante; può essere {@code null} se non ancora determinato
     * @param buildingId identificativo dell'edificio in cui si svolge la partita; può essere {@code null}
     * @param gameId identificativo del gioco associato alla partita; può essere {@code null}
     * @param sessionId identificativo della sessione di gioco; può essere {@code null}
     * @param winner identificativo del partecipante vincitore; può essere {@code null} se la partita non è conclusa
     * @param status stato corrente della partita; non può essere {@code null}
     * @param scheduledAt istante in cui la partita è programmata; può essere {@code null}
     * @param playedAt istante in cui la partita è stata giocata; può essere {@code null}
     * @param resultData dati di dettaglio dell'esito della partita; può essere {@code null}
     * @throws IllegalArgumentException se uno dei vincoli sui parametri obbligatori non è rispettato
     */
    public TournamentMatch(TournamentMatchId matchId, TournamentId tournamentId, int round, int bracketPosition,
                           String participantA, String participantB, String buildingId, String gameId,
                           String sessionId, String winner, TournamentMatchStatus status,
                           Instant scheduledAt, Instant playedAt, String resultData) {
        if (matchId == null) throw new IllegalArgumentException("matchId cannot be null");
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (round < 1) throw new IllegalArgumentException("round must be >= 1");
        if (bracketPosition < 1) throw new IllegalArgumentException("bracketPosition must be >= 1");
        if (participantA == null || participantA.isBlank()) throw new IllegalArgumentException("participantA cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
        this.matchId = matchId;
        this.tournamentId = tournamentId;
        this.round = round;
        this.bracketPosition = bracketPosition;
        this.participantA = participantA;
        this.participantB = participantB;
        this.buildingId = buildingId;
        this.gameId = gameId;
        this.sessionId = sessionId;
        this.winner = winner;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.playedAt = playedAt;
        this.resultData = resultData;
    }

    /**
     * Restituisce l'identificativo univoco della partita.
     *
     * @return l'identificativo della partita, mai {@code null}
     */
    public TournamentMatchId getMatchId() {
        return matchId;
    }

    /**
     * Restituisce l'identificativo del torneo a cui la partita appartiene.
     *
     * @return l'identificativo del torneo, mai {@code null}
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }

    /**
     * Restituisce il numero del round nel tabellone.
     *
     * @return il numero del round, sempre maggiore o uguale a 1
     */
    public int getRound() {
        return round;
    }

    /**
     * Restituisce la posizione della partita all'interno del round.
     *
     * @return la posizione nel tabellone, sempre maggiore o uguale a 1
     */
    public int getBracketPosition() {
        return bracketPosition;
    }

    /**
     * Restituisce l'identificativo del primo partecipante.
     *
     * @return l'identificativo del primo partecipante, mai {@code null} né vuoto
     */
    public String getParticipantA() {
        return participantA;
    }

    /**
     * Restituisce l'identificativo del secondo partecipante.
     *
     * @return l'identificativo del secondo partecipante, oppure {@code null} se non ancora determinato
     */
    public String getParticipantB() {
        return participantB;
    }

    /**
     * Restituisce l'identificativo dell'edificio in cui si svolge la partita.
     *
     * @return l'identificativo dell'edificio, oppure {@code null} se non specificato
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce l'identificativo del gioco associato alla partita.
     *
     * @return l'identificativo del gioco, oppure {@code null} se non specificato
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco.
     *
     * @return l'identificativo della sessione, oppure {@code null} se non specificato
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'identificativo del partecipante vincitore.
     *
     * @return l'identificativo del vincitore, oppure {@code null} se la partita non è conclusa
     */
    public String getWinner() {
        return winner;
    }

    /**
     * Restituisce lo stato corrente della partita.
     *
     * @return lo stato della partita, mai {@code null}
     */
    public TournamentMatchStatus getStatus() {
        return status;
    }

    /**
     * Restituisce l'istante in cui la partita è programmata.
     *
     * @return l'istante di programmazione, oppure {@code null} se non specificato
     */
    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Restituisce l'istante in cui la partita è stata giocata.
     *
     * @return l'istante di svolgimento, oppure {@code null} se la partita non è stata giocata
     */
    public Instant getPlayedAt() {
        return playedAt;
    }

    /**
     * Restituisce i dati di dettaglio dell'esito della partita.
     *
     * @return i dati dell'esito, oppure {@code null} se non disponibili
     */
    public String getResultData() {
        return resultData;
    }

    /**
     * Confronta questa partita con un altro oggetto verificandone l'uguaglianza
     * sulla base dell'identificativo della partita.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code TournamentMatch} con lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentMatch that = (TournamentMatch) o;
        return Objects.equals(matchId, that.matchId);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo della partita.
     *
     * @return il codice hash della partita
     */
    @Override
    public int hashCode() {
        return Objects.hash(matchId);
    }
}
