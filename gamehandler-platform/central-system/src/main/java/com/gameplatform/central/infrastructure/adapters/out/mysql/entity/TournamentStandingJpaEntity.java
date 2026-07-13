package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tournament_standings")
@IdClass(TournamentStandingId.class)
public class TournamentStandingJpaEntity {
    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;
    @Id
    @Column(name = "participant_id", length = 36, nullable = false)
    private String participantId;
    @Column(name = "wins", nullable = false)
    private Integer wins;
    @Column(name = "losses", nullable = false)
    private Integer losses;
    @Column(name = "points", nullable = false)
    private Integer points;
    @Column(name = "`rank`")
    private Integer rank;

    public TournamentStandingJpaEntity() {
    }

    public TournamentStandingJpaEntity(String tournamentId, String participantId, Integer wins, Integer losses,
                                       Integer points, Integer rank) {
        this.tournamentId = tournamentId;
        this.participantId = participantId;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
        this.rank = rank;
    }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public Integer getWins() { return wins; }
    public void setWins(Integer wins) { this.wins = wins; }
    public Integer getLosses() { return losses; }
    public void setLosses(Integer losses) { this.losses = losses; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
}