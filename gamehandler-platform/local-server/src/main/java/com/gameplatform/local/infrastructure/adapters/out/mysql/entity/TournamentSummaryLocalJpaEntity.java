package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournaments_summary_local} (PIANO §7.B). Read-only
 * replica updated only by sync; no {@code @OneToMany}, no {@code @Version}
 * (mirror of {@code GameDefinitionLocalJpaEntity} /
 * {@code TournamentMatchLocalJpaEntity}). The {@code buildingIds} {@link List}
 * is serialised as a JSON {@link String} in a {@code TEXT} column (handled by
 * {@code TournamentSummaryLocalMapper}, like {@code registration_rules} on
 * {@code GameDefinitionLocalJpaEntity}). {@code deleted} defaults to
 * {@code false}; the sync service physically removes the row on a
 * {@code deleted=true} tombstone, so a stored row always has
 * {@code deleted=false}.
 */
@Entity
@Table(name = "tournaments_summary_local")
public class TournamentSummaryLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "team_based", nullable = false)
    private Boolean teamBased;

    @Column(name = "team_size", nullable = false)
    private Integer teamSize;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "building_ids", columnDefinition = "TEXT")
    private String buildingIdsJson;

    @Column(name = "participants_count", nullable = false)
    private Integer participantsCount;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TournamentSummaryLocalJpaEntity() {
    }

    public TournamentSummaryLocalJpaEntity(String tournamentId, String name, String gameType, Boolean teamBased,
                                           Integer teamSize, String status, Instant startsAt, Instant endsAt,
                                           String buildingIdsJson, Integer participantsCount, Boolean deleted,
                                           Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.name = name;
        this.gameType = gameType;
        this.teamBased = teamBased;
        this.teamSize = teamSize;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.buildingIdsJson = buildingIdsJson;
        this.participantsCount = participantsCount;
        this.deleted = deleted;
        this.updatedAt = updatedAt;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public Boolean getTeamBased() {
        return teamBased;
    }

    public void setTeamBased(Boolean teamBased) {
        this.teamBased = teamBased;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public String getBuildingIdsJson() {
        return buildingIdsJson;
    }

    public void setBuildingIdsJson(String buildingIdsJson) {
        this.buildingIdsJson = buildingIdsJson;
    }

    public Integer getParticipantsCount() {
        return participantsCount;
    }

    public void setParticipantsCount(Integer participantsCount) {
        this.participantsCount = participantsCount;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
