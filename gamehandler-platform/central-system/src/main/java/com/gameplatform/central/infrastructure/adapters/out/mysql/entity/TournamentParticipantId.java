package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

public class TournamentParticipantId implements Serializable {
    private String tournamentId;
    private String participantId;

    public TournamentParticipantId() {
    }

    public TournamentParticipantId(String tournamentId, String participantId) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
    }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentParticipantId that = (TournamentParticipantId) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(participantId, that.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, participantId);
    }
}