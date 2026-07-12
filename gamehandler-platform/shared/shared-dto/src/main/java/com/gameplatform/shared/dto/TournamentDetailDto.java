package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a single tournament with its full detail
 * (summary + standings + matches + participants) for the Local
 * {@code GET /api/tournaments/{id}} endpoint (PIANO §7.B). Aggregates
 * the four local replicas into a single response payload.
 *
 * @param summary       the tournament summary projection
 * @param standings     the standings rows (possibly empty)
 * @param matches       the local match rows (possibly empty)
 * @param participants  the registered participants (possibly empty)
 */
public record TournamentDetailDto(
        TournamentSummaryDto summary,
        List<TournamentStandingDto> standings,
        List<TournamentMatchDto> matches,
        List<TournamentParticipantViewDto> participants
) {
}
