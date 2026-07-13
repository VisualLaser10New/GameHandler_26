package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TeamMemberLocalJpaEntity}
 * ({@code tournament_id}, {@code team_id}, {@code user_id}) — local mirror of
 * the Central {@code TournamentTeamMemberId} shape extended with
 * {@code tournament_id} (BUG-TEAM-3).
 */
public class TeamMemberLocalId implements Serializable {

    private String tournamentId;
    private String teamId;
    private String userId;

    public TeamMemberLocalId() {
    }

    public TeamMemberLocalId(String tournamentId, String teamId, String userId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMemberLocalId that = (TeamMemberLocalId) o;
        return Objects.equals(tournamentId, that.tournamentId)
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tournamentId, teamId, userId);
    }
}