package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.dto.TournamentDetailDto;

import java.util.Optional;

/**
 * Use case (PIANO §7.B): returns the detail view of a single tournament,
 * aggregating the four local replicas ({@code tournaments_summary_local},
 * {@code tournament_standings_local}, {@code tournament_matches_local},
 * {@code tournament_participants_local}).
 */
public interface GetTournamentDetailUseCase {

    Optional<TournamentDetailDto> getDetail(String tournamentId);
}