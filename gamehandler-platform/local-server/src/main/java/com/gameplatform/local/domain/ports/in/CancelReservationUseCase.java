package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;

public interface CancelReservationUseCase {

    /**
     * Cancels the reservation without an ownership check. Reserved for internal
     * / superuser paths (reservation expiration scheduler, PLATFORM_ADMIN
     * bypass). Players must use {@link #cancel(ReservationId, UserId)} which
     * enforces ownership (Verifica 2 — VIOL-4 IDOR fix).
     */
    void cancel(ReservationId reservationId);

    /**
     * Cancels the reservation after enforcing that {@code actingUserId} is the
     * reservation's owner; throws {@code AccessDeniedException} otherwise.
     * Used by the REST {@code DELETE /api/reservations/{id}} endpoint to close
     * the previous IDOR (any PLAYER could cancel any reservation if it knew
     * the id), Verifica 2 — VIOL-4.
     */
    void cancel(ReservationId reservationId, UserId actingUserId);
}
