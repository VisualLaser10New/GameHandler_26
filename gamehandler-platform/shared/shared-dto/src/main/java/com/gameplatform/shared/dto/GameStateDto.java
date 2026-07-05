package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.GameMachineStatus;

public record GameStateDto(
    String gameId,
    GameType gameType,
    String name,
    String buildingId,
    GameMachineStatus status,
    int minPlayers,
    int maxPlayers
) {
    /** Backward-compatible constructor for callers that don't provide player limits. */
    public GameStateDto(String gameId, GameType gameType, String name, String buildingId, GameMachineStatus status) {
        this(gameId, gameType, name, buildingId, status, 1, Integer.MAX_VALUE);
    }
}
