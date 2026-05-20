package com.gameplatform.shared.mqtt.payload;

public record SessionPausePayload(
    String sessionId,
    String pausedBy
) {}
