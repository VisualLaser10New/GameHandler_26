package com.gameplatform.shared.dto;

import java.time.Instant;

public record StatisticsDto(
    String buildingId,
    String gameType,
    Instant periodStart,
    Instant periodEnd,
    Integer totalSessions,
    Integer avgDuration,
    Integer totalReservations,
    String data,
    Integer totalAbortedSessions
) {}
