package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import java.io.Serializable;
import java.util.Objects;

public class TournamentTeamMemberId implements Serializable {
    private String teamId;
    private String userId;

    public TournamentTeamMemberId() {
    }

    public TournamentTeamMemberId(String teamId, String userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TournamentTeamMemberId that = (TournamentTeamMemberId) o;
        return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, userId);
    }
}