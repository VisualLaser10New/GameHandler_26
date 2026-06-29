package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug L-04: {@link Reservation#confirm()} has no state validation.
 *
 * <p>The {@code confirm()} method unconditionally sets status to CONFIRMED without
 * checking the current state. This allows confirming a CANCELLED or EXPIRED reservation,
 * which should be invalid transitions.</p>
 */
class BugL04_ReservationConfirmNoStateValidationTest {

    private static final Instant NOW = Instant.parse("2026-06-29T08:00:00Z");

    private Reservation createPendingReservation() {
        return new Reservation(
                new ReservationId("res-1"),
                new GameId("game-1"),
                new UserId("user-1"),
                ReservationStatus.PENDING,
                NOW.minusSeconds(300),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(600)
        );
    }

    @Test
    @DisplayName("BUG L-04: A CANCELLED reservation cannot be confirmed — throws InvalidGameStateTransitionException")
    void cancelledReservationCanBeConfirmed() {
        Reservation reservation = createPendingReservation();

        // Cancel the reservation
        reservation.cancel();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());

        // confirm() fails on a CANCELLED reservation
        assertThrows(InvalidGameStateTransitionException.class, reservation::confirm);
    }

    @Test
    @DisplayName("BUG L-04: An EXPIRED reservation cannot be confirmed — throws InvalidGameStateTransitionException")
    void expiredReservationCanBeConfirmed() {
        Reservation reservation = createPendingReservation();

        // Expire the reservation
        reservation.expire();
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());

        // confirm() fails on an EXPIRED reservation
        assertThrows(InvalidGameStateTransitionException.class, reservation::confirm);
    }

    @Test
    @DisplayName("BUG L-04: A CONFIRMED reservation cannot be confirmed again — throws InvalidGameStateTransitionException")
    void alreadyConfirmedReservationCanBeConfirmedAgain() {
        Reservation reservation = createPendingReservation();

        // Confirm once (valid)
        reservation.confirm();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());

        // Confirm again throws exception
        assertThrows(InvalidGameStateTransitionException.class, reservation::confirm);
    }
}
