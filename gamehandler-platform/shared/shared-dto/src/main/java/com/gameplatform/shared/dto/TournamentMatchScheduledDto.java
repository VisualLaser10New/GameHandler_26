package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.time.Instant;

public record TournamentMatchScheduledDto(
        String eventId,
        String eventType,
        String matchId,
        String tournamentId,
        int round,
        int bracketPosition,
        String participantA,
        String participantB,
        GameType gameType,
        String gameId,
        String status,
        Instant scheduledAt,
        String buildingId
) {
}
