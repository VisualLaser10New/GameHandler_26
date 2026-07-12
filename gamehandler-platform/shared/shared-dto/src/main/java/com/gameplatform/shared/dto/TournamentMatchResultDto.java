package com.gameplatform.shared.dto;

public record TournamentMatchResultDto(
        String matchId,
        String winner,
        String resultData,
        String status
) {
}
