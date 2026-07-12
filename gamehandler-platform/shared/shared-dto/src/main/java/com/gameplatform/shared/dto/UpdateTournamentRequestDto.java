package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Request body for {@code PUT /api/tournaments/{id}} (use case §7.A.1).
 * Carries the three mutable fields of a {@code DRAFT} tournament:
 * {@code name}, {@code startsAt} and {@code buildingIds}. Validation messages
 * mirror {@link CreateTournamentRequestDto}.
 */
public record UpdateTournamentRequestDto(
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "startsAt must not be null") Instant startsAt,
        @NotNull(message = "buildingIds must not be null")
        @Size(min = 2, message = "buildingIds must contain at least 2 buildings") List<String> buildingIds
) {
}