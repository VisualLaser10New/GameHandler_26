package main.java.com.gameplatform.shared.dto;

import java.time.Instant;

public record AlertDto(
    String buildingId,
    String gameId,
    String alertType,
    String message,
    Instant timestamp
) {}
