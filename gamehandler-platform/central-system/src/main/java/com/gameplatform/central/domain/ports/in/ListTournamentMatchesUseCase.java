package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentMatchDto;

import java.util.List;

/**
 * Read-only delegation to {@code TournamentMatchRepository.findByTournament}.
 * Returns the full match list of a tournament — including {@code BYE} rows.
 * No status filtering applied.
 */
public interface ListTournamentMatchesUseCase {
    List<TournamentMatchDto> findByTournament(TournamentId tournamentId);
}
