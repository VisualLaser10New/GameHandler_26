package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only local replica of a single tournament participant row
 * (PIANO §7.B), the flattened Central→Local projection of
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} events. Pure Java POJO,
 * immutable, identity = ({@code tournamentId}, {@code participantId})
 * composite key — mirror of the Central {@code TournamentParticipant}
 * model plus the extra {@code updatedAt} envelope field used by the
 * local read views and the team-match membership extension
 * (@code PlayerTournamentController.myMatches}).
 */
public class TournamentParticipantLocal {

    private final TournamentId tournamentId;
    private final String participantId;
    private final boolean isTeam;
    private final String displayName;
    private final Instant registeredAt;
    private final Instant updatedAt;

    /**
     * Costruisce una nuova replica locale di un partecipante a un torneo.
     *
     * @param tournamentId  identificatore del torneo (non null)
     * @param participantId identificatore del partecipante (non blank)
     * @param isTeam        true se il partecipante è una squadra
     * @param displayName   nome visualizzato del partecipante (non blank)
     * @param registeredAt  istante di registrazione (non null)
     * @param updatedAt     istante dell'ultimo aggiornamento (non null)
     * @throws IllegalArgumentException se tournamentId, participantId o displayName sono null/blank,
     *                                  o se registeredAt o updatedAt sono null
     */
    public TournamentParticipantLocal(TournamentId tournamentId, String participantId, boolean isTeam,
                                      String displayName, Instant registeredAt, Instant updatedAt) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("participantId cannot be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("registeredAt cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.isTeam = isTeam;
        this.displayName = displayName;
        this.registeredAt = registeredAt;
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
     * Indica se il partecipante è una squadra.
     *
     * @return true se è una squadra
     */
    public boolean isTeam() {
        return isTeam;
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
     * Restituisce l'istante di registrazione al torneo.
     *
     * @return registeredAt
     */
    public Instant getRegisteredAt() {
        return registeredAt;
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
     * Confronta questo partecipante con un altro oggetto per uguaglianza
     * basata su tournamentId e participantId.
     *
     * @param o l'oggetto da confrontare
     * @return true se gli oggetti sono uguali
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipantLocal that = (TournamentParticipantLocal) o;
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
