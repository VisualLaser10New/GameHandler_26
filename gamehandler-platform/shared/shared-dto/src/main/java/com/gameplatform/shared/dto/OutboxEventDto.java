package main.java.com.gameplatform.shared.dto;

import java.time.Instant;

public record OutboxEventDto(
    String eventId,
    String eventType,
    String payload,
    Instant createdAt
) {}
