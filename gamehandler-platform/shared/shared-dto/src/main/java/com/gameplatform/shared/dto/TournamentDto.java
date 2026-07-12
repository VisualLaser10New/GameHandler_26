package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.time.Instant;
import java.util.List;

public record TournamentDto(
        String id,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        TournamentStatus status,
        Instant startsAt,
        Instant endsAt,
        List<String> buildings,
        int participantsCount
) {
}
