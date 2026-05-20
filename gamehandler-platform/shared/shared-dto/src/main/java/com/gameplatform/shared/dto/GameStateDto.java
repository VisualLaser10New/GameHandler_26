package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.GameMachineStatus;

public record GameStateDto(
    String gameId,
    GameType gameType,
    String name,
    String buildingId,
    GameMachineStatus status
) {}
