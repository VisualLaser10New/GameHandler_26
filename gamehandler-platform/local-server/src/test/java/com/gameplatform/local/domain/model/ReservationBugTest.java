package com.gameplatform.local.domain.model;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests exposing bugs in the Reservation domain model.
 */
class ReservationBugTest {

    private static final ReservationId RES_ID = new ReservationId("res-1");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final UserId USER_ID = new UserId("user-1");

    /**
     * BUG #4: Reservation state transitions have no guards.
     *
     * confirm(), cancel(), and expire() methods (lines 63-73) have NO status precondition checks.
     * Any status can transition to any other status:
     * - EXPIRED -> CONFIRMED (re-confirming an expired reservation)
     * - CANCELLED -> CONFIRMED (resurrecting a cancelled reservation)
     * - CONFIRMED -> CANCELLED then CONFIRMED again
     *
     * Impact: Data corruption - reservations can be manipulated into invalid states.
     */
    @Test
    @DisplayName("BUG #4: EXPIRED reservation cannot be confirmed - throws InvalidGameStateTransitionException")
    void confirm_allowsFromExpiredStatus() {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(
                RES_ID, GAME_ID, USER_ID, ReservationStatus.PENDING,
                now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(3)), now
        );

        reservation.expire();
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());

        // confirm() has guard
        assertThrows(InvalidGameStateTransitionException.class, reservation::confirm);
    }

    @Test
    @DisplayName("BUG #4b: CANCELLED reservation cannot be confirmed - throws InvalidGameStateTransitionException")
    void confirm_allowsFromCancelledStatus() {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(
                RES_ID, GAME_ID, USER_ID, ReservationStatus.PENDING,
                now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(3)), now
        );

        reservation.cancel();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());

        // confirm() has guard
        assertThrows(InvalidGameStateTransitionException.class, reservation::confirm);
    }

    @Test
    @DisplayName("BUG #4c: CONFIRMED reservation cannot be expired - throws InvalidGameStateTransitionException")
    void expire_allowsFromConfirmedStatus() {
        Instant now = Instant.now();
        Reservation reservation = new Reservation(
                RES_ID, GAME_ID, USER_ID, ReservationStatus.PENDING,
                now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(3)), now
        );

        reservation.confirm();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());

        // expire() should succeed
        assertDoesNotThrow(reservation::expire);
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
    }

    /**
     * BUG #5: canBeCancelled uses Instant.now(clock) but the service passes Clock object.
     * The method returns false for CONFIRMED reservations even when they should be cancellable.
     *
     * canBeCancelled() (line 59) only checks `status == ReservationStatus.PENDING`.
     * A CONFIRMED reservation cannot be cancelled through canBeCancelled(),
     * but the ReservationService.cancel() (line 143) uses canBeCancelled() to guard.
     * This means CONFIRMED reservations can NEVER be cancelled, even if they're far in the future.
     *
     * Impact: Users cannot cancel confirmed reservations, which is a business logic error.
     */
    @Test
    @DisplayName("BUG #5: CONFIRMED reservation cannot be cancelled via canBeCancelled()")
    void canBeCancelled_returnsFalseForConfirmed() {
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));

        Reservation reservation = new Reservation(
                RES_ID, GAME_ID, USER_ID, ReservationStatus.PENDING,
                now.plus(Duration.ofHours(5)),  // well in the future
                now.plus(Duration.ofHours(6)),
                now
        );

        // While PENDING, can be cancelled
        assertTrue(reservation.canBeCancelled(clock));

        // After confirming...
        reservation.confirm();

        // BUG: canBeCancelled returns false for CONFIRMED reservations
        assertFalse(reservation.canBeCancelled(clock),
                "BUG CONFIRMED: CONFIRMED reservation returns false for canBeCancelled");
    }
}
