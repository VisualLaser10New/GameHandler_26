package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "tournament_team_members")
@IdClass(TournamentTeamMemberId.class)
public class TournamentTeamMemberJpaEntity {
    @Id
    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;
    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    public TournamentTeamMemberJpaEntity() {
    }

    public TournamentTeamMemberJpaEntity(String teamId, String userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}