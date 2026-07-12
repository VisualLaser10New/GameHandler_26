package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

public record ScheduleTournamentMatchesDto(
        @NotBlank(message = "tournamentId must not be blank") String tournamentId
) {
}
