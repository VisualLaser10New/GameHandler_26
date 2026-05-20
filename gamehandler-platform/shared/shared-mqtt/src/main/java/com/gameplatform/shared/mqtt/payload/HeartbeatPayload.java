package main.java.com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

public record HeartbeatPayload(
    String gameId,
    Instant timestamp
) {}
