package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug L-06: the point-5 contract says expiration must process reservations with
 * status IN (PENDING, CONFIRMED), but Reservation.expire() currently accepts only PENDING.
 */
class BugL06_ConfirmedReservationExpirationTest {

    private static final Instant NOW = Instant.parse("2026-06-29T08:00:00Z");

    @Test
    @DisplayName("BUG L-06: a CONFIRMED reservation that reached endTime must be expirable")
    void confirmedReservationShouldBeExpirable() {
        Reservation reservation = new Reservation(
                new ReservationId("res-confirmed-expired"),
                new GameId("game-1"),
                new UserId("user-1"),
                ReservationStatus.CONFIRMED,
                NOW.minusSeconds(7200),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(10800)
        );

        assertDoesNotThrow(reservation::expire,
                "ReservationExpirationService is specified to expire PENDING and CONFIRMED reservations; " +
                "CONFIRMED must not throw InvalidGameStateTransitionException.");
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
    }
}
