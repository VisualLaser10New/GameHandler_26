package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tournament_buildings")
@IdClass(TournamentBuildingId.class)
public class TournamentBuildingJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "building_id", length = 100, nullable = false)
    private String buildingId;

    public TournamentBuildingJpaEntity() {
    }

    public TournamentBuildingJpaEntity(String tournamentId, String buildingId) {
        this.tournamentId = tournamentId;
        this.buildingId = buildingId;
    }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }
}