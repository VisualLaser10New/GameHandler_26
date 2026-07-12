package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Null-safe mapper for the {@link Team} central domain model and the
 * {@link TournamentTeamJpaEntity}/{@link TournamentTeamMemberJpaEntity}
 * persistence entities ({@code tournament_teams}/{@code tournament_team_members}
 * tables, FASE 4 PIANO &sect;3.2). {@code @Component} instance bean (matches
 * {@code GameDefinitionMapper}/{@code PlayerStatisticsMapper}); absorbs the
 * members &harr; {@code List<UserId>} boundary conversion since membership is
 * stored in a separate join-table. {@code toDomain} accepts a nullable members
 * list (resolving to an empty list) and {@code toMemberEntities} resolves a
 * null domain to {@code List.of()}.
 */
@Component
public class TeamMapper {

    public Team toDomain(TournamentTeamJpaEntity team, List<TournamentTeamMemberJpaEntity> members) {
        if (team == null) {
            return null;
        }
        List<UserId> memberIds = (members == null)
                ? List.of()
                : members.stream().map(m -> new UserId(m.getUserId())).toList();
        return new Team(
                new TeamId(team.getId()),
                new TournamentId(team.getTournamentId()),
                team.getName(),
                memberIds,
                team.getCreatedAt()
        );
    }

    public TournamentTeamJpaEntity toTeamEntity(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentTeamJpaEntity(
                domain.getTeamId().value(),
                domain.getTournamentId().value(),
                domain.getName(),
                domain.getCreatedAt()
        );
    }

    public List<TournamentTeamMemberJpaEntity> toMemberEntities(Team domain) {
        if (domain == null) {
            return List.of();
        }
        return domain.getMembers().stream()
                .map(m -> new TournamentTeamMemberJpaEntity(domain.getTeamId().value(), m.value()))
                .toList();
    }
}