package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

public class TournamentBuildingId implements Serializable {
    private String tournamentId;
    private String buildingId;

    public TournamentBuildingId() {
    }

    public TournamentBuildingId(String tournamentId, String buildingId) {
        this.tournamentId = tournamentId;
        this.buildingId = buildingId;
    }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentBuildingId that = (TournamentBuildingId) o;
        return Objects.equals(tournamentId, that.tournamentId) && Objects.equals(buildingId, that.buildingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, buildingId);
    }
}