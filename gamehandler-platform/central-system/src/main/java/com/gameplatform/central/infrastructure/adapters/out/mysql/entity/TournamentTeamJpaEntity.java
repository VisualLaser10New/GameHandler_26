package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tournament_teams")
public class TournamentTeamJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TournamentTeamJpaEntity() {
    }

    public TournamentTeamJpaEntity(String id, String tournamentId, String name, Instant createdAt) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}