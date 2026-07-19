package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only local replica of a single tournament standings row
 * (PIANO §7.B), the flattened Central→Local projection of
 * {@code TOURNAMENT_STANDINGS_UPSERTED} events. Pure Java POJO,
 * immutable, identity = ({@code tournamentId}, {@code participantId})
 * composite key — mirror of the Central {@code TournamentStanding} model
 * plus the extra {@code displayName} and {@code updatedAt} envelope
 * fields needed for the local read views.
 */
public class TournamentStandingLocal {

    private final TournamentId tournamentId;
    private final String participantId;
    private final String displayName;
    private final int wins;
    private final int losses;
    private final int points;
    private final Integer rank;
    private final Instant updatedAt;

    /**
     * Costruisce una nuova replica locale di una classifica di torneo.
     *
     * @param tournamentId  identificatore del torneo (non null)
     * @param participantId identificatore del partecipante (non blank)
     * @param displayName   nome visualizzato del partecipante (non blank)
     * @param wins          numero di vittorie (>= 0)
     * @param losses        numero di sconfitte (>= 0)
     * @param points        punteggio totale (>= 0)
     * @param rank          posizione in classifica (può essere null se non ancora calcolata)
     * @param updatedAt     istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se tournamentId, participantId o displayName sono null/blank,
     *                                  se wins, losses o points sono negativi,
     *                                  o se updatedAt è null
     */
    public TournamentStandingLocal(TournamentId tournamentId, String participantId, String displayName,
                                   int wins, int losses, int points, Integer rank, Instant updatedAt) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("participantId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins must be >= 0");
        }
        if (losses < 0) {
            throw new IllegalArgumentException("losses must be >= 0");
        }
        if (points < 0) {
            throw new IllegalArgumentException("points must be >= 0");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.displayName = displayName;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
        this.updatedAt = updatedAt;
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
     * Restituisce l'identificatore del partecipante.
     *
     * @return participantId
     */
    public String getParticipantId() {
        return participantId;
    }

    /**
     * Restituisce il nome visualizzato del partecipante.
     *
     * @return displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Restituisce il numero di vittorie.
     *
     * @return wins
     */
    public int getWins() {
        return wins;
    }

    /**
     * Restituisce il numero di sconfitte.
     *
     * @return losses
     */
    public int getLosses() {
        return losses;
    }

    /**
     * Restituisce il punteggio totale.
     *
     * @return points
     */
    public int getPoints() {
        return points;
    }

    /**
     * Restituisce la posizione in classifica.
     *
     * @return rank, o null se non ancora calcolata
     */
    public Integer getRank() {
        return rank;
    }

    /**
     * Restituisce l'istante dell'ultimo aggiornamento.
     *
     * @return updatedAt
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Confronta questa classifica con un altro oggetto per uguaglianza
     * basata su tournamentId e participantId.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStandingLocal that = (TournamentStandingLocal) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(participantId, that.participantId);
    }

    /**
     * Restituisce l'hash code basato su tournamentId e participantId.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
