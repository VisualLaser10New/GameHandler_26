package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.central.domain.ports.out.TournamentTeamRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TeamMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentTeamJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentTeamMemberJpaRepository;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentTeamRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape but constructor-injects <em>two</em> JPA repositories ({@code tournament_teams}
 * + {@code tournament_team_members}) per FASE 4 PIANO &sect;3.2 &amp; decision C.2:
 * {@link #save} is an atomic delete-all-then-insert over the members join-table
 * (writes carry the default {@code @Transactional} propagation so the whole
 * replace-members operation commits as one). All reads are marked
 * {@code @Transactional(readOnly = true)} and are null-safe
 * ({@code Optional.empty()} / {@code List.of()} / {@code false} on {@code null} args).
 */
@Component
public class TournamentTeamRepositoryAdapter implements TournamentTeamRepository {

    private final TournamentTeamJpaRepository teamRepo;
    private final TournamentTeamMemberJpaRepository memberRepo;
    private final TeamMapper mapper;

    public TournamentTeamRepositoryAdapter(TournamentTeamJpaRepository teamRepo,
                                          TournamentTeamMemberJpaRepository memberRepo,
                                          TeamMapper mapper) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Team save(Team team) {
        if (team == null) {
            return null;
        }
        teamRepo.save(mapper.toTeamEntity(team));
        memberRepo.deleteByTeamId(team.getTeamId().value());
        List<TournamentTeamMemberJpaEntity> memberEntities = mapper.toMemberEntities(team);
        for (TournamentTeamMemberJpaEntity memberEntity : memberEntities) {
            memberRepo.save(memberEntity);
        }
        return team;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findById(TeamId teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        Optional<TournamentTeamJpaEntity> teamOpt = teamRepo.findById(teamId.value());
        if (teamOpt.isEmpty()) {
            return Optional.empty();
        }
        List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(teamId.value());
        return Optional.of(mapper.toDomain(teamOpt.get(), members));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentTeamJpaEntity> teams = teamRepo.findByTournamentId(tournamentId.value());
        if (teams == null) {
            return List.of();
        }
        List<Team> result = new ArrayList<>(teams.size());
        for (TournamentTeamJpaEntity team : teams) {
            List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(team.getId());
            result.add(mapper.toDomain(team, members));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findByTournamentAndName(TournamentId tournamentId, String name) {
        if (tournamentId == null || name == null) {
            return Optional.empty();
        }
        return teamRepo.findByTournamentIdAndName(tournamentId.value(), name)
                .map(team -> {
                    List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(team.getId());
                    return mapper.toDomain(team, members);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findByTournamentAndMember(TournamentId tournamentId, UserId memberUserId) {
        if (tournamentId == null || memberUserId == null) {
            return Optional.empty();
        }
        List<String> teamIds = memberRepo.findTeamIdsByUserId(memberUserId.value());
        if (teamIds == null) {
            return Optional.empty();
        }
        for (String teamId : teamIds) {
            Optional<TournamentTeamJpaEntity> teamOpt = teamRepo.findById(teamId);
            if (teamOpt.isPresent() && teamOpt.get().getTournamentId().equals(tournamentId.value())) {
                List<TournamentTeamMemberJpaEntity> members = memberRepo.findByTeamId(teamId);
                return Optional.of(mapper.toDomain(teamOpt.get(), members));
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndName(TournamentId tournamentId, String name) {
        if (tournamentId == null || name == null) {
            return false;
        }
        return teamRepo.existsByTournamentIdAndName(tournamentId.value(), name);
    }

    @Override
    @Transactional
    public void deleteById(TeamId teamId) {
        if (teamId == null) {
            return;
        }
        memberRepo.deleteByTeamId(teamId.value());
        teamRepo.deleteById(teamId.value());
    }
}