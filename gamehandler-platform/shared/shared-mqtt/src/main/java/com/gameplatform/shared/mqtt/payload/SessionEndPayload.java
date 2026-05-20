package main.java.com.gameplatform.shared.mqtt.payload;

import main.java.com.gameplatform.shared.domain.model.WinCondition;

public record SessionEndPayload(
    String sessionId,
    String winnerId,
    WinCondition winCondition,
    String resultData
) {}
