package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_matches_local} (PIANO §3.4 lines 416-427).
 * Read-only replica updated only by sync; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code GameDefinitionLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_matches_local")
public class TournamentMatchLocalJpaEntity {

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

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "game_id", length = 100)
    private String gameId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public TournamentMatchLocalJpaEntity() {
    }

    /**
     * Costruisce un nuovo incontro torneo locale con tutti i campi.
     *
     * @param id              identificatore univoco dell'incontro
     * @param tournamentId    identificativo del torneo
     * @param round           numero del round
     * @param bracketPosition posizione nel bracket
     * @param participantA    identificativo del primo partecipante
     * @param participantB    identificativo del secondo partecipante (può essere {@code null})
     * @param gameType        tipo di gioco dell'incontro
     * @param gameId          identificativo della postazione gioco (può essere {@code null})
     * @param status          stato dell'incontro
     * @param scheduledAt     data/hora programmata (può essere {@code null})
     */
    public TournamentMatchLocalJpaEntity(String id, String tournamentId, Integer round, Integer bracketPosition,
                                         String participantA, String participantB, String gameType,
                                         String gameId, String status, Instant scheduledAt) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.round = round;
        this.bracketPosition = bracketPosition;
        this.participantA = participantA;
        this.participantB = participantB;
        this.gameType = gameType;
        this.gameId = gameId;
        this.status = status;
        this.scheduledAt = scheduledAt;
    }

    /**
     * Restituisce l'identificatore univoco dell'incontro.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco dell'incontro.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce l'identificativo del torneo.
     *
     * @return tournamentId
     */
    public String getTournamentId() {
        return tournamentId;
    }

    /**
     * Imposta l'identificativo del torneo.
     *
     * @param tournamentId nuovo identificativo torneo
     */
    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    /**
     * Restituisce il numero del round.
     *
     * @return round
     */
    public Integer getRound() {
        return round;
    }

    /**
     * Imposta il numero del round.
     *
     * @param round nuovo numero round
     */
    public void setRound(Integer round) {
        this.round = round;
    }

    /**
     * Restituisce la posizione nel bracket.
     *
     * @return bracketPosition
     */
    public Integer getBracketPosition() {
        return bracketPosition;
    }

    /**
     * Imposta la posizione nel bracket.
     *
     * @param bracketPosition nuova posizione bracket
     */
    public void setBracketPosition(Integer bracketPosition) {
        this.bracketPosition = bracketPosition;
    }

    /**
     * Restituisce l'identificativo del primo partecipante.
     *
     * @return participantA
     */
    public String getParticipantA() {
        return participantA;
    }

    /**
     * Imposta l'identificativo del primo partecipante.
     *
     * @param participantA nuovo identificativo primo partecipante
     */
    public void setParticipantA(String participantA) {
        this.participantA = participantA;
    }

    /**
     * Restituisce l'identificativo del secondo partecipante.
     *
     * @return participantB (può essere {@code null})
     */
    public String getParticipantB() {
        return participantB;
    }

    /**
     * Imposta l'identificativo del secondo partecipante.
     *
     * @param participantB nuovo identificativo secondo partecipante
     */
    public void setParticipantB(String participantB) {
        this.participantB = participantB;
    }

    /**
     * Restituisce il tipo di gioco dell'incontro.
     *
     * @return gameType
     */
    public String getGameType() {
        return gameType;
    }

    /**
     * Imposta il tipo di gioco dell'incontro.
     *
     * @param gameType nuovo tipo di gioco
     */
    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    /**
     * Restituisce l'identificativo della postazione gioco associata.
     *
     * @return gameId (può essere {@code null})
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Imposta l'identificativo della postazione gioco.
     *
     * @param gameId nuovo identificativo postazione
     */
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    /**
     * Restituisce lo stato dell'incontro.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato dell'incontro.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce la data/hora programmata dell'incontro.
     *
     * @return scheduledAt (può essere {@code null})
     */
    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Imposta la data/hora programmata dell'incontro.
     *
     * @param scheduledAt nuova data/hora programmata
     */
    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}