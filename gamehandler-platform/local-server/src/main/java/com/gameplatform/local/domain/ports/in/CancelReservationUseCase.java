package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.ReservationId;

public interface CancelReservationUseCase {
    void cancel(ReservationId reservationId);
}
