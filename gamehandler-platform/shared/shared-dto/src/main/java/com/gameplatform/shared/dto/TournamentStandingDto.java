package com.gameplatform.shared.dto;

public record TournamentStandingDto(
        String participantId,
        String displayName,
        int wins,
        int losses,
        int points,
        Integer rank
) {
}
