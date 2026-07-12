package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_standings_local} (PIANO §7.B).
 * Read-only replica updated only by sync; composite PK
 * ({@code tournamentId}, {@code participantId}) via
 * {@link TournamentStandingLocalId}; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code TournamentMatchLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_standings_local", indexes = {
        @Index(name = "idx_tsl_tournament", columnList = "tournament_id")
})
@IdClass(TournamentStandingLocalId.class)
public class TournamentStandingLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Id
    @Column(name = "participant_id", length = 64, nullable = false)
    private String participantId;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "wins", nullable = false)
    private Integer wins;

    @Column(name = "losses", nullable = false)
    private Integer losses;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TournamentStandingLocalJpaEntity() {
    }

    public TournamentStandingLocalJpaEntity(String tournamentId, String participantId, String displayName,
                                            Integer wins, Integer losses, Integer points, Integer rank,
                                            Instant updatedAt) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.displayName = displayName;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
        this.updatedAt = updatedAt;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
