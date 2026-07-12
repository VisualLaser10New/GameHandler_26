package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.time.Instant;
import java.util.Map;

public record GameDefinitionEventDto(
        String eventId,
        String eventType,
        GameType gameType,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules,
        Instant updatedAt
) {
}