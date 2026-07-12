package com.gameplatform.shared.dto;

import java.util.List;

public record RegisterTournamentParticipantDto(
        String teamName,
        List<String> teamMembers
) {
}
