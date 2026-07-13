package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * JPA entity for {@code team_members_local} (BUG-TEAM-3). Read-only replica
 * of the Central {@code tournament_team_members} membership, replicated via
 * outbox {@code TEAM_MEMBERS_UPSERTED}. Composite PK
 * ({@code tournamentId}, {@code teamId}, {@code userId}) via
 * {@link TeamMemberLocalId}; no {@code @OneToMany}, no {@code @Version}
 * (mirror of {@link TournamentParticipantLocalJpaEntity}). The
 * {@code idx_tml_user} index backs the {@code myMatches} JPQL join on
 * {@code user_id}.
 */
@Entity
@Table(name = "team_members_local", indexes = {
        @Index(name = "idx_tml_user", columnList = "user_id")
})
@IdClass(TeamMemberLocalId.class)
public class TeamMemberLocalJpaEntity {

    @Id
    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Id
    @Column(name = "team_id", length = 36, nullable = false)
    private String teamId;

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    public TeamMemberLocalJpaEntity() {
    }

    public TeamMemberLocalJpaEntity(String tournamentId, String teamId, String userId) {
        this.tournamentId = tournamentId;
        this.teamId = teamId;
        this.userId = userId;
    }

    public String getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(String tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}