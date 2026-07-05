package com.gameplatform.shared.mqtt.payload;

public record LobbyCancelPayload(
    String sessionId,
    String userId
) {}
