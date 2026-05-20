package main.java.com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

public record AlertPayload(
    String alertType,
    String gameId,
    String message,
    Instant timestamp
) {}
