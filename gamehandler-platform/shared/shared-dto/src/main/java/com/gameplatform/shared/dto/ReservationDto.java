package main.java.com.gameplatform.shared.dto;

import java.time.Instant;
import main.java.com.gameplatform.shared.domain.model.ReservationStatus;

public record ReservationDto(
    String id,
    String gameId,
    String userId,
    ReservationStatus status,
    Instant startTime,
    Instant endTime
) {}
