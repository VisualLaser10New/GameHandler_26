package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code tournament_matches} del database MySQL.
 *
 * <p>Rappresenta un singolo incontro di un torneo, con la sua posizione nel
 * tabellone (turno e posizione) e i partecipanti coinvolti. Può essere associato
 * a un edificio, a una sessione di gioco e a un vincitore. I campi
 * {@code participantB}, {@code buildingId}, {@code gameId}, {@code sessionId},
 * {@code winner}, {@code scheduledAt}, {@code playedAt} e {@code resultData}
 * possono essere {@code null} a seconda dello stato di avanzamento dell'incontro.
 * Non sono dichiarate relazioni JPA: i riferimenti sono mantenuti come colonne
 * testuali, secondo la convenzione esagonale adottata nel progetto.</p>
 *
 * @see TournamentJpaEntity
 * @see TournamentParticipantJpaEntity
 */
@Entity
@Table(name = "tournament_matches")
public class TournamentMatchJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Column(name = "round", nullable = false)
    private Integer round;
    @Column(name = "bracket_position", nullable = false)
    private Integer bracketPosition;
    @Column(name = "participant_a", length = 36, nullable = false)
    private String participantA;
    @Column(name = "participant_b", length = 36)
    private String participantB;
    @Column(name = "building_id", length = 100)
    private String buildingId;
    @Column(name = "game_id", length = 100)
    private String gameId;
    @Column(name = "session_id", length = 36)
    private String sessionId;
    @Column(name = "winner", length = 36)
    private String winner;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Column(name = "played_at")
    private Instant playedAt;
    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public TournamentMatchJpaEntity() {
    }

    /**
     * Costruisce un incontro di torneo con i dati di tabellone e di esito forniti.
     *
     * @param id identificativo univoco dell'incontro; non deve essere {@code null}
     * @param tournamentId identificativo del torneo di appartenenza; non deve essere {@code null}
     * @param round turno del torneo a cui appartiene l'incontro; non deve essere {@code null} e non negativo
     * @param bracketPosition posizione dell'incontro nel tabellone; non deve essere {@code null} e non negativa
     * @param participantA identificativo del primo partecipante; non deve essere {@code null}
     * @param participantB identificativo del secondo partecipante; può essere {@code null}
     * @param buildingId identificativo dell'edificio sede dell'incontro; può essere {@code null}
     * @param gameId identificativo della partita di gioco associata; può essere {@code null}
     * @param sessionId identificativo della sessione di gioco associata; può essere {@code null}
     * @param winner identificativo del partecipante vincitore; può essere {@code null}
     * @param status stato corrente dell'incontro; non deve essere {@code null}
     * @param scheduledAt istante programmato dell'incontro; può essere {@code null}
     * @param playedAt istante di svolgimento dell'incontro; può essere {@code null}
     * @param resultData dati di risultato aggiuntivi in formato testuale; possono essere {@code null}
     */
    public TournamentMatchJpaEntity(String id, String tournamentId, Integer round, Integer bracketPosition,
                                    String participantA, String participantB, String buildingId, String gameId,
                                    String sessionId, String winner, String status, Instant scheduledAt,
                                    Instant playedAt, String resultData) {
        this.id = id;
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
     * Restituisce l'identificativo univoco dell'incontro.
     *
     * @return l'identificativo dell'incontro; non deve essere {@code null}
     */
    public String getId() { return id; }

    /**
     * Imposta l'identificativo univoco dell'incontro.
     *
     * @param id nuovo identificativo dell'incontro; può essere {@code null}
     */
    public void setId(String id) { this.id = id; }

    /**
     * Restituisce l'identificativo del torneo di appartenenza.
     *
     * @return l'identificativo del torneo; non deve essere {@code null}
     */
    public String getTournamentId() { return tournamentId; }

    /**
     * Imposta l'identificativo del torneo di appartenenza.
     *
     * @param tournamentId nuovo identificativo del torneo; non deve essere {@code null}
     */
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    /**
     * Restituisce il turno del torneo a cui appartiene l'incontro.
     *
     * @return il turno dell'incontro; non deve essere {@code null} e non negativo
     */
    public Integer getRound() { return round; }

    /**
     * Imposta il turno del torneo a cui appartiene l'incontro.
     *
     * @param round nuovo turno dell'incontro; non deve essere {@code null} e non negativo
     */
    public void setRound(Integer round) { this.round = round; }

    /**
     * Restituisce la posizione dell'incontro all'interno del tabellone.
     *
     * @return la posizione nel tabellone; non deve essere {@code null} e non negativa
     */
    public Integer getBracketPosition() { return bracketPosition; }

    /**
     * Imposta la posizione dell'incontro all'interno del tabellone.
     *
     * @param bracketPosition nuova posizione nel tabellone; non deve essere {@code null} e non negativa
     */
    public void setBracketPosition(Integer bracketPosition) { this.bracketPosition = bracketPosition; }

    /**
     * Restituisce l'identificativo del primo partecipante.
     *
     * @return l'identificativo del primo partecipante; non deve essere {@code null}
     */
    public String getParticipantA() { return participantA; }

    /**
     * Imposta l'identificativo del primo partecipante.
     *
     * @param participantA nuovo identificativo del primo partecipante; non deve essere {@code null}
     */
    public void setParticipantA(String participantA) { this.participantA = participantA; }

    /**
     * Restituisce l'identificativo del secondo partecipante.
     *
     * @return l'identificativo del secondo partecipante; può essere {@code null}
     */
    public String getParticipantB() { return participantB; }

    /**
     * Imposta l'identificativo del secondo partecipante.
     *
     * @param participantB nuovo identificativo del secondo partecipante; può essere {@code null}
     */
    public void setParticipantB(String participantB) { this.participantB = participantB; }

    /**
     * Restituisce l'identificativo dell'edificio sede dell'incontro.
     *
     * @return l'identificativo dell'edificio; può essere {@code null}
     */
    public String getBuildingId() { return buildingId; }

    /**
     * Imposta l'identificativo dell'edificio sede dell'incontro.
     *
     * @param buildingId nuovo identificativo dell'edificio; può essere {@code null}
     */
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    /**
     * Restituisce l'identificativo della partita di gioco associata.
     *
     * @return l'identificativo della partita; può essere {@code null}
     */
    public String getGameId() { return gameId; }

    /**
     * Imposta l'identificativo della partita di gioco associata.
     *
     * @param gameId nuovo identificativo della partita; può essere {@code null}
     */
    public void setGameId(String gameId) { this.gameId = gameId; }

    /**
     * Restituisce l'identificativo della sessione di gioco associata.
     *
     * @return l'identificativo della sessione; può essere {@code null}
     */
    public String getSessionId() { return sessionId; }

    /**
     * Imposta l'identificativo della sessione di gioco associata.
     *
     * @param sessionId nuovo identificativo della sessione; può essere {@code null}
     */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    /**
     * Restituisce l'identificativo del partecipante vincitore.
     *
     * @return l'identificativo del vincitore; può essere {@code null} se l'incontro non è concluso
     */
    public String getWinner() { return winner; }

    /**
     * Imposta l'identificativo del partecipante vincitore.
     *
     * @param winner nuovo identificativo del vincitore; può essere {@code null}
     */
    public void setWinner(String winner) { this.winner = winner; }

    /**
     * Restituisce lo stato corrente dell'incontro.
     *
     * @return lo stato dell'incontro; non deve essere {@code null}
     */
    public String getStatus() { return status; }

    /**
     * Imposta lo stato corrente dell'incontro.
     *
     * @param status nuovo stato dell'incontro; non deve essere {@code null}
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * Restituisce l'istante programmato per lo svolgimento dell'incontro.
     *
     * @return l'istante programmato; può essere {@code null} se non ancora fissato
     */
    public Instant getScheduledAt() { return scheduledAt; }

    /**
     * Imposta l'istante programmato per lo svolgimento dell'incontro.
     *
     * @param scheduledAt nuovo istante programmato; può essere {@code null}
     */
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    /**
     * Restituisce l'istante di effettivo svolgimento dell'incontro.
     *
     * @return l'istante di svolgimento; può essere {@code null} se l'incontro non è ancora stato giocato
     */
    public Instant getPlayedAt() { return playedAt; }

    /**
     * Imposta l'istante di effettivo svolgimento dell'incontro.
     *
     * @param playedAt nuovo istante di svolgimento; può essere {@code null}
     */
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }

    /**
     * Restituisce i dati di risultato aggiuntivi dell'incontro.
     *
     * @return i dati di risultato in formato testuale; possono essere {@code null}
     */
    public String getResultData() { return resultData; }

    /**
     * Imposta i dati di risultato aggiuntivi dell'incontro.
     *
     * @param resultData nuovi dati di risultato in formato testuale; possono essere {@code null}
     */
    public void setResultData(String resultData) { this.resultData = resultData; }
}