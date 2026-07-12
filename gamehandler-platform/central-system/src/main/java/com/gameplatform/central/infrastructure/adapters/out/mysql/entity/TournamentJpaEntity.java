package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tournaments")
public class TournamentJpaEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;
    @Column(name = "team_based", nullable = false)
    private Boolean teamBased;
    @Column(name = "team_size", nullable = false)
    private Integer teamSize;
    @Column(name = "format", length = 30, nullable = false)
    private String format;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "created_by", length = 36, nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TournamentJpaEntity() {
    }

    public TournamentJpaEntity(String id, String name, String gameType, Boolean teamBased, Integer teamSize,
                               String format, String status, Instant startsAt, Instant endsAt,
                               String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.format = format;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public Boolean getTeamBased() { return teamBased; }
    public void setTeamBased(Boolean teamBased) { this.teamBased = teamBased; }
    public Integer getTeamSize() { return teamSize; }
    public void setTeamSize(Integer teamSize) { this.teamSize = teamSize; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}