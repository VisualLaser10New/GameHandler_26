package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.WinCondition;

public record SessionEndPayload(
    String sessionId,
    String winnerId,
    WinCondition winCondition,
    String resultData
) {}
