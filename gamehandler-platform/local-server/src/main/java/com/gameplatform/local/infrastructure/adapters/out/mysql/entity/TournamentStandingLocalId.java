package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TournamentStandingLocalJpaEntity}
 * ({@code tournament_id}, {@code participant_id}) — mirror of the Central
 * {@code TournamentParticipantId} shape.
 */
public class TournamentStandingLocalId implements Serializable {

    private String tournamentId;
    private String participantId;

    public TournamentStandingLocalId() {
    }

    public TournamentStandingLocalId(String tournamentId, String participantId) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentStandingLocalId that = (TournamentStandingLocalId) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}
