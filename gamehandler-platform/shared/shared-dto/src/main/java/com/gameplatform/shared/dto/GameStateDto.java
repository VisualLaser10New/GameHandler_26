package main.java.com.gameplatform.shared.dto;

import main.java.com.gameplatform.shared.domain.model.GameType;
import main.java.com.gameplatform.shared.domain.model.GameMachineStatus;

public record GameStateDto(
    String gameId,
    GameType gameType,
    String name,
    String buildingId,
    GameMachineStatus status
) {}
