package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.domain.model.TournamentId;

/**
 * Out-port for the {@code team_members_local} read-only replica (BUG-TEAM-3).
 * {@code save} is an idempotent upsert by the composite PK
 * ({@code tournamentId}, {@code teamId}, {@code userId}); the sync service
 * physically removes a tournament's full team→user membership snapshot via
 * {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency) before re-inserting the fresh snapshot.
 */
public interface TeamMembersLocalRepository {

    void save(String tournamentId, String teamId, String userId);

    void deleteByTournament(TournamentId tournamentId);
}