package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only replica of a tournament match destined for THIS building
 * (PIANO §3.4). NO {@code buildingId} (the table only holds matches routed
 * to this building — ambiguity O), NO {@code winner}, NO {@code playedAt},
 * NO {@code resultData} (those are central-only). Pure Java POJO, immutable,
 * identity = {@code id}. {@code status} is mutable via a new-instance
 * {@code withStatus(...)} helper so the sync service can flip SCHEDULED →
 * IN_PROGRESS → COMPLETED/ABANDONED idempotently.
 */
public class TournamentMatchLocal {

    private final TournamentMatchId id;
    private final TournamentId tournamentId;
    private final int round;
    private final int bracketPosition;
    private final String participantA;
    private final String participantB;   // nullable (BYE never replicated, but kept nullable)
    private final GameType gameType;
    private final String gameId;          // nullable
    private final TournamentMatchStatus status;
    private final Instant scheduledAt;    // nullable

    /**
     * Costruisce una nuova replica locale di un match di torneo.
     *
     * @param id              identificatore del match (non null)
     * @param tournamentId    identificatore del torneo (non null)
     * @param round           numero del round
     * @param bracketPosition posizione nel bracket
     * @param participantA    identificatore del primo partecipante (non blank)
     * @param participantB    identificatore del secondo partecipante (può essere null per BYE)
     * @param gameType        tipo di gioco (non null)
     * @param gameId          identificatore della postazione di gioco assegnata (può essere null)
     * @param status          stato del match (non null)
     * @param scheduledAt     istante programmato (può essere null)
     * @throws IllegalArgumentException se id, tournamentId, participantA, gameType o status sono null/blank
     */
    public TournamentMatchLocal(TournamentMatchId id, TournamentId tournamentId, int round, int bracketPosition,
                                String participantA, String participantB, GameType gameType,
                                String gameId, TournamentMatchStatus status, Instant scheduledAt) {
        if (id == null) {
            throw new IllegalArgumentException("TournamentMatchId cannot be null");
        }
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (participantA == null || participantA.isBlank()) {
            throw new IllegalArgumentException("participantA cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("TournamentMatchStatus cannot be null");
        }
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
     * Restituisce l'identificatore del match.
     *
     * @return id
     */
    public TournamentMatchId getId() {
        return id;
    }

    /**
     * Restituisce l'identificatore del torneo.
     *
     * @return tournamentId
     */
    public TournamentId getTournamentId() {
        return tournamentId;
    }

    /**
     * Restituisce il numero del round.
     *
     * @return round
     */
    public int getRound() {
        return round;
    }

    /**
     * Restituisce la posizione nel bracket.
     *
     * @return bracketPosition
     */
    public int getBracketPosition() {
        return bracketPosition;
    }

    /**
     * Restituisce l'identificatore del primo partecipante.
     *
     * @return participantA
     */
    public String getParticipantA() {
        return participantA;
    }

    /**
     * Restituisce l'identificatore del secondo partecipante.
     *
     * @return participantB, o null se non presente (BYE)
     */
    public String getParticipantB() {
        return participantB;
    }

    /**
     * Restituisce il tipo di gioco del match.
     *
     * @return gameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Restituisce l'identificatore della postazione di gioco assegnata.
     *
     * @return gameId, o null se non ancora assegnata
     */
    public String getGameId() {
        return gameId;
    }

    /**
     * Restituisce lo stato corrente del match.
     *
     * @return status
     */
    public TournamentMatchStatus getStatus() {
        return status;
    }

    /**
     * Restituisce l'istante programmato per il match.
     *
     * @return scheduledAt, o null se non specificato
     */
    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Crea una nuova copia immutabile del match con lo stato aggiornato.
     * Utilizzato dai flussi di inizio, completamento e aborto del match.
     *
     * @param newStatus nuovo stato del match (non null)
     * @return nuova istanza di TournamentMatchLocal con lo stato aggiornato
     * @throws IllegalArgumentException se newStatus è null
     */
    public TournamentMatchLocal withStatus(TournamentMatchStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("TournamentMatchStatus cannot be null");
        }
        return new TournamentMatchLocal(
                this.id,
                this.tournamentId,
                this.round,
                this.bracketPosition,
                this.participantA,
                this.participantB,
                this.gameType,
                this.gameId,
                newStatus,
                this.scheduledAt
        );
    }

    /**
     * Confronta questo match con un altro oggetto per uguaglianza basata su id.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentMatchLocal that = (TournamentMatchLocal) o;
        return Objects.equals(id, that.id);
    }

    /**
     * Restituisce l'hash code basato su id.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}