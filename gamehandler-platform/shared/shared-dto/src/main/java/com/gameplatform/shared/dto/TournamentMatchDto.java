package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import java.time.Instant;

public record TournamentMatchDto(
        String id,
        int round,
        int bracketPosition,
        String participantA,
        String participantB,
        String buildingId,
        String gameId,
        TournamentMatchStatus status,
        Instant scheduledAt,
        String winner
) {
}
