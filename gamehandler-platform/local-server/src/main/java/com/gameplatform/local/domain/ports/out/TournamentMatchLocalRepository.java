package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code tournament_matches_local} read-only replica.
 * {@code save} is an idempotent upsert by PK {@code id} (mirror of
 * {@link GameDefinitionLocalRepository#save}).
 */
public interface TournamentMatchLocalRepository {
    TournamentMatchLocal save(TournamentMatchLocal match);
    Optional<TournamentMatchLocal> findById(TournamentMatchId id);
    List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId);
    List<TournamentMatchLocal> findScheduledByParticipant(String userId);
    void deleteById(TournamentMatchId id);
}