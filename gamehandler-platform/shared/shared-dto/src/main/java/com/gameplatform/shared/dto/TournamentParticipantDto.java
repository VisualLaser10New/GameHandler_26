package com.gameplatform.shared.dto;

public record TournamentParticipantDto(
        String participantId,
        boolean isTeam,
        String displayName
) {
}
