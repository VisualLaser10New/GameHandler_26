package com.gameplatform.shared.mqtt.payload;

public record LobbyLeavePayload(
    String sessionId,
    String userId
) {}
