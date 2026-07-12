package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding a game to a building's catalog (LOCAL_ADMIN).
 *
 * @param gameType the game type literal (must match a {@code GameType} enum value,
 *                 e.g. {@code CHESS}, {@code FOOSBALL})
 * @param name     the human-readable name of the device/table (e.g. "Chess Table 2")
 */
public record CreateGameRequestDto(
        @NotBlank String gameType,
        @NotBlank String name
) {
}