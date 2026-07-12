package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentStandingDto;

import java.util.List;

/**
 * Reads the current standings for a tournament, sorted by
 * {@code points desc, wins desc}. When all rows are zero-scored
 * (pre-tournament seed state established by the FASE 5 bracket scheduler),
 * {@code rank} is left {@code null}; final ranking assignment is FASE 6.
 */
public interface GetTournamentStandingsUseCase {
    List<TournamentStandingDto> getStandings(TournamentId tournamentId);
}
