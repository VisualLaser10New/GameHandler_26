package main.java.com.gameplatform.shared.mqtt.payload;

public record SessionPausePayload(
    String sessionId,
    String pausedBy
) {}
