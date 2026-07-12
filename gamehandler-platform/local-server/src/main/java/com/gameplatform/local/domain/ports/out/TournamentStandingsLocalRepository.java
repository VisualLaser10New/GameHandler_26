package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;

/**
 * Out-port for the {@code tournament_standings_local} read-only replica
 * (PIANO §7.B). {@code save} is an idempotent upsert by the composite
 * PK ({@code tournamentId}, {@code participantId}); the sync service
 * physically removes a tournament's full standings snapshot via
 * {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency).
 */
public interface TournamentStandingsLocalRepository {

    TournamentStandingLocal save(TournamentStandingLocal standing);

    List<TournamentStandingLocal> findByTournament(TournamentId tournamentId);

    void deleteByTournament(TournamentId tournamentId);

    boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}