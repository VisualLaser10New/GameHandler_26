package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(ReservationId id);
    List<Reservation> findByUserId(UserId userId);
    List<Reservation> findByGameId(GameId gameId);
    List<Reservation> findByStatus(ReservationStatus status);
    List<Reservation> findExpired(Instant now);
}
