package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import java.util.Map;

public record GameDefinitionDto(
        GameType gameType,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules
) {
}