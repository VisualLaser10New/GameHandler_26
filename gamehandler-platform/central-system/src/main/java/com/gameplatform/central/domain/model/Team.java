package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a registered team within a tournament, owned by
 * the Central Source-of-Truth ({@code teams} table, FASE 4 PIANO &sect;3.2).
 *
 * <p>Pure Java (no framework annotations), mirroring the
 * {@code GameDefinition}/{@code PlayerStatistics} POJO convention. Identity
 * is the {@code teamId} (primary key); the {@code (tournamentId, teamId)}
 * pair is unique. Fully immutable in FASE 4: the {@code members} list is
 * defensively copied via {@link List#copyOf} at construction and exposed
 * unchanged by {@link #getMembers()}. Membership-cardinality validation
 * against {@link Tournament#getTeamSize()} is delegated to
 * {@code TournamentRegistrationService}, not enforced here, so that teams
 * reconstructed from the repository can bypass the check.</p>
 */
public class Team {
    private final TeamId teamId;
    private final TournamentId tournamentId;
    private final String name;
    private final List<UserId> members;
    private final Instant createdAt;

    public Team(TeamId teamId, TournamentId tournamentId, String name, List<UserId> members, Instant createdAt) {
        if (teamId == null) throw new IllegalArgumentException("teamId cannot be null");
        if (tournamentId == null) throw new IllegalArgumentException("tournamentId cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (members == null) throw new IllegalArgumentException("members cannot be null");
        if (createdAt == null) throw new IllegalArgumentException("createdAt cannot be null");
        this.teamId = teamId;
        this.tournamentId = tournamentId;
        this.name = name;
        this.members = List.copyOf(members);
        this.createdAt = createdAt;
    }

    public TeamId getTeamId() {
        return teamId;
    }

    public TournamentId getTournamentId() {
        return tournamentId;
    }

    public String getName() {
        return name;
    }

    public List<UserId> getMembers() {
        return members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team that = (Team) o;
        return Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId);
    }
}
