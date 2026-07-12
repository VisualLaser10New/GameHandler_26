package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a tournament summary for the Local player
 * tournament list endpoint {@code GET /api/tournaments[?status=]}
 * (PIANO §7.B). Sourced from the {@code tournaments_summary_local}
 * replica. Mirrors the persisted {@code TournamentSummaryLocal} domain
 * projection but excludes the {@code deleted} tombstone flag (only
 * non-deleted rows are returned).
 *
 * @param tournamentId       the tournament id
 * @param name               the tournament display name
 * @param gameType           the game type
 * @param teamBased          whether the tournament is team-based
 * @param teamSize           the team size (1 for individual)
 * @param status             the tournament status
 * @param startsAt           the scheduled start instant
 * @param endsAt             the scheduled end instant (nullable)
 * @param buildingIds        the buildings hosting the tournament
 * @param participantsCount  the registered participants count
 * @param updatedAt          the last mutation instant
 */
public record TournamentSummaryDto(
        String tournamentId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        TournamentStatus status,
        Instant startsAt,
        Instant endsAt,
        List<String> buildingIds,
        int participantsCount,
        Instant updatedAt
) {
}
