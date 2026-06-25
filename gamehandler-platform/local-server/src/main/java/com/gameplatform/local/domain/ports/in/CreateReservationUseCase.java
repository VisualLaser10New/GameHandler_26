package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;

public interface CreateReservationUseCase {
    Reservation create(GameId gameId, UserId userId, Instant start, Instant end);
}
