package main.java.com.gameplatform.shared.dto;

import java.time.Instant;

public record CreateReservationRequestDto(
    String gameId,
    String userId,
    Instant startTime,
    Instant endTime
) {}
