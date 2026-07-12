package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;

import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a single completed game session for the
 * {@code GET /api/players/me/matches/history} Local player endpoint
 * (PIANO §7.B). Excludes the {@code GameResult} detail blob and the
 * raw {@code participants} list of {@link GameSessionDto}; the Local
 * endpoint applies a {@code status == COMPLETED} Java filter on
 * {@code GameSessionRepository.findByParticipant} results and projects
 * each session into this view.
 *
 * @param sessionId         the game session id
 * @param gameType          the game type
 * @param startedAt         the session start instant
 * @param endedAt           the session end instant (nullable only if the
 *                          session is somehow returned without an end)
 * @param durationSeconds   the effective play duration in seconds
 * @param winnerId          the winner user id (nullable for team-based
 *                          sessions where the winner is a {@code TeamId})
 * @param winCondition      the win condition
 * @param participants      the list of participant user ids
 */
public record PlayerMatchDto(
        String sessionId,
        GameType gameType,
        Instant startedAt,
        Instant endedAt,
        Integer durationSeconds,
        String winnerId,
        WinCondition winCondition,
        List<String> participants
) {
}
