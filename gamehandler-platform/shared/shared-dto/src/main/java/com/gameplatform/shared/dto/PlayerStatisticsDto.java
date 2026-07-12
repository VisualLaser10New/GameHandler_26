package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;

/**
 * DTO representing a player's aggregated statistics for a single game type,
 * returned by both the Central read-model ({@code player_statistics}, source of
 * truth) and the Local on-demand computation ({@code StatisticsService}, derived
 * from {@code game_sessions}+{@code session_participants}).
 *
 * <p>Defined in {@code shared-dto} so the Central and Local REST adapters expose
 * the exact same shape to clients (FASE 3, PIANO &sect;2.4/&sect;2.5).</p>
 *
 * @param userId        the player's {@link com.gameplatform.shared.domain.model.UserId} value
 * @param gameType      the game type these statistics refer to
 * @param matchesPlayed number of completed matches the player participated in
 * @param matchesWon    number of those matches the player won
 * @param lastPlayedAt  when the player last played this game type (null if never)
 */
public record PlayerStatisticsDto(
        String userId,
        GameType gameType,
        int matchesPlayed,
        int matchesWon,
        Instant lastPlayedAt
) {
}