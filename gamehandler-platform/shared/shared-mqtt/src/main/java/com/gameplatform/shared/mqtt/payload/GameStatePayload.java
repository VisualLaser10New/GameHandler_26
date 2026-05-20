package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.GameMachineStatus;

public record GameStatePayload(
    String gameId,
    GameMachineStatus status,
    String userId
) {}
