package main.java.com.gameplatform.shared.mqtt.payload;

import main.java.com.gameplatform.shared.domain.model.GameMachineStatus;

public record GameStatePayload(
    String gameId,
    GameMachineStatus status,
    String userId
) {}
