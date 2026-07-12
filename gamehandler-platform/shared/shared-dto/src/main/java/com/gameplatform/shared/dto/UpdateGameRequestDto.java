package com.gameplatform.shared.dto;

/**
 * Request body for updating a game in a building's catalog (LOCAL_ADMIN).
 *
 * <p>Both fields are optional (nullable); at least one must be present (validated
 * by the service). {@code status}, when provided, must be one of the
 * {@code GameMachineStatus} enum literals handled by the admin flow (currently
 * {@code AVAILABLE} and {@code MAINTENANCE}).</p>
 *
 * @param name   the new human-readable name, or null to leave unchanged
 * @param status the new {@code GameMachineStatus} literal ({@code AVAILABLE} or
 *               {@code MAINTENANCE}), or null to leave unchanged
 */
public record UpdateGameRequestDto(
        String name,
        String status
) {
}