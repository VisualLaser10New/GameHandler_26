package main.java.com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

public record HeartbeatAckPayload(
    String gameId,
    Instant serverTimestamp
) {}
