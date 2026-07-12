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

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public boolean isTeam() {
        return isTeam;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipantLocal that = (TournamentParticipantLocal) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
