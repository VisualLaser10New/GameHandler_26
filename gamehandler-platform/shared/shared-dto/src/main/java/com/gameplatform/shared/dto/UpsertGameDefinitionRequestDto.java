package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record UpsertGameDefinitionRequestDto(
        @NotNull GameType gameType,
        @NotBlank String name,
        @Min(1) @Max(100) int minPlayers,
        @Min(1) @Max(100) int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules
) {
}