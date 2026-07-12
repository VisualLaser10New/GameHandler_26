package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import java.util.List;
import java.util.Optional;

public interface TournamentMatchRepository {
    TournamentMatch save(TournamentMatch match);
    Optional<TournamentMatch> findById(TournamentMatchId id);
    List<TournamentMatch> findByTournament(TournamentId tournamentId);
    void deleteById(TournamentMatchId id);
    Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id);
    Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition);
}
