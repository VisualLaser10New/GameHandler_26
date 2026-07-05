package com.gameplatform.shared.mqtt.payload;

public record LobbyJoinPayload(
    String sessionId,
    String userId
) {}
