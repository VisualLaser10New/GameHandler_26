package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Team;
import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

public interface TournamentTeamRepository {
    Team save(Team team);
    Optional<Team> findById(TeamId teamId);
    List<Team> findByTournament(TournamentId tournamentId);
    Optional<Team> findByTournamentAndName(TournamentId tournamentId, String name);
    Optional<Team> findByTournamentAndMember(TournamentId tournamentId, UserId memberUserId);
    boolean existsByTournamentAndName(TournamentId tournamentId, String name);
    void deleteById(TeamId teamId);
}
