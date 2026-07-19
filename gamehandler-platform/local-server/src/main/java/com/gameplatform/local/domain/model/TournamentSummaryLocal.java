package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read-only local replica of a tournament summary (PIANO §7.B), the flattened
 * Central→Local projection of {@code TOURNAMENT_SUMMARY_UPSERTED} events.
 *
 * <p>NO {@code eventId} / {@code eventType} / {@code originatingRequestId}
 * (those are outbox-envelope fields that the sync service consumes but does not
 * persist on the projection). Pure Java POJO, immutable, identity =
 * {@code tournamentId} — mirror of {@link TournamentMatchLocal} and
 * {@link GameDefinitionLocal}. The {@code deleted} flag is the column that a
 * tombstone {@code deleted=true} upstream event cleans up via
 * {@code deleteById}; on the projection it is stored for read-side filtering
 * (the sync service PHYSICALLY deletes the row on a tombstone, so a
 * non-deleted projection row always has {@code deleted=false}).</p>
 */
public class TournamentSummaryLocal {

    private final TournamentId tournamentId;
    private final String name;
    private final GameType gameType;
    private final boolean teamBased;
    private final int teamSize;
    private final TournamentStatus status;
    private final Instant startsAt;
    private final Instant endsAt;
    private final List<String> buildingIds;
    private final int participantsCount;
    private final boolean deleted;
    private final Instant updatedAt;

    /**
     * Costruisce una nuova replica locale di un riepilogo di torneo.
     *
     * @param tournamentId      identificatore del torneo (non null)
     * @param name              nome del torneo (non blank)
     * @param gameType          tipo di gioco (non null)
     * @param teamBased         true se il torneo è a squadre
     * @param teamSize          dimensione delle squadre
     * @param status            stato del torneo (non null)
     * @param startsAt          istante di inizio (può essere null)
     * @param endsAt            istante di fine (può essere null)
     * @param buildingIds       lista degli edifici coinvolti (può essere null)
     * @param participantsCount numero di partecipanti
     * @param deleted           flag di eliminazione logica
     * @param updatedAt         istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se tournamentId, name, gameType, status o updatedAt sono null/blank
     */
    public TournamentSummaryLocal(TournamentId tournamentId, String name, GameType gameType, boolean teamBased,
                                  int teamSize, TournamentStatus status, Instant startsAt, Instant endsAt,
                                  List<String> buildingIds, int participantsCount, boolean deleted, Instant updatedAt) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (gameType == null) {
            throw new IllegalArgumentException("GameType cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("TournamentStatus cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.buildingIds = buildingIds != null ? List.copyOf(buildingIds) : List.of();
        this.participantsCount = participantsCount;
        this.deleted = deleted;
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
     * Restituisce il nome del torneo.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Restituisce il tipo di gioco del torneo.
     *
     * @return gameType
     */
    public GameType getGameType() {
        return gameType;
    }

    /**
     * Indica se il torneo è a squadre.
     *
     * @return true se a squadre
     */
    public boolean isTeamBased() {
        return teamBased;
    }

    /**
     * Restituisce la dimensione delle squadre.
     *
     * @return teamSize
     */
    public int getTeamSize() {
        return teamSize;
    }

    /**
     * Restituisce lo stato del torneo.
     *
     * @return status
     */
    public TournamentStatus getStatus() {
        return status;
    }

    /**
     * Restituisce l'istante di inizio del torneo.
     *
     * @return startsAt, o null se non specificato
     */
    public Instant getStartsAt() {
        return startsAt;
    }

    /**
     * Restituisce l'istante di fine del torneo.
     *
     * @return endsAt, o null se non specificato
     */
    public Instant getEndsAt() {
        return endsAt;
    }

    /**
     * Restituisce la lista degli edifici coinvolti.
     *
     * @return buildingIds
     */
    public List<String> getBuildingIds() {
        return buildingIds;
    }

    /**
     * Restituisce il numero di partecipanti.
     *
     * @return participantsCount
     */
    public int getParticipantsCount() {
        return participantsCount;
    }

    /**
     * Indica se il torneo è stato eliminato logicamente.
     *
     * @return true se eliminato
     */
    public boolean isDeleted() {
        return deleted;
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
     * Confronta questo riepilogo con un altro oggetto per uguaglianza basata su tournamentId.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentSummaryLocal that = (TournamentSummaryLocal) o;
        return Objects.equals(tournamentId, that.tournamentId);
    }

    /**
     * Restituisce l'hash code basato su tournamentId.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(tournamentId);
    }
}
