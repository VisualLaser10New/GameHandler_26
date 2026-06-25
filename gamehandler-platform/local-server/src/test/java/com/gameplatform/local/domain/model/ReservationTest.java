package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReservationTest {

    @Test
    void shouldCreateReservationSuccessfully() {
        ReservationId id = new ReservationId("res-1");
        GameId gameId = new GameId("game-1");
        UserId userId = new UserId("user-1");
        Instant start = Instant.parse("2026-06-25T12:00:00Z");
        Instant end = Instant.parse("2026-06-25T13:00:00Z");
        Instant created = Instant.parse("2026-06-25T10:00:00Z");

        Reservation reservation = new Reservation(id, gameId, userId, ReservationStatus.PENDING, start, end, created);

        assertEquals(id, reservation.getId());
        assertEquals(gameId, reservation.getGameId());
        assertEquals(userId, reservation.getUserId());
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        assertEquals(start, reservation.getStartTime());
        assertEquals(end, reservation.getEndTime());
        assertEquals(created, reservation.getCreatedAt());
    }

    @Test
    void shouldConfirmReservation() {
        Reservation reservation = createSampleReservation(ReservationStatus.PENDING);
        reservation.confirm();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    void shouldCancelReservation() {
        Reservation reservation = createSampleReservation(ReservationStatus.PENDING);
        reservation.cancel();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
    }

    @Test
    void shouldExpireReservation() {
        Reservation reservation = createSampleReservation(ReservationStatus.CONFIRMED);
        reservation.expire();
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
    }

    @Test
    void shouldAllowCancellationIfPendingAndMoreThanOneHourBeforeStart() {
        Instant startTime = Instant.parse("2026-06-25T12:00:00Z");
        Reservation reservation = new Reservation(
            new ReservationId("res-1"),
            new GameId("game-1"),
            new UserId("user-1"),
            ReservationStatus.PENDING,
            startTime,
            Instant.parse("2026-06-25T13:00:00Z"),
            Instant.parse("2026-06-25T10:00:00Z")
        );

        // Clock set at 10:30 (1h 30m before start) -> Should be able to cancel
        Clock clock1 = Clock.fixed(Instant.parse("2026-06-25T10:30:00Z"), ZoneId.of("UTC"));
        assertTrue(reservation.canBeCancelled(clock1));

        // Clock set at 11:00 (exactly 1h before start) -> Should NOT be able to cancel (manca almeno 1 ora all'inizio - strictly more than 1 hour)
        Clock clock2 = Clock.fixed(Instant.parse("2026-06-25T11:00:00Z"), ZoneId.of("UTC"));
        assertFalse(reservation.canBeCancelled(clock2));

        // Clock set at 11:30 (30m before start) -> Should NOT be able to cancel
        Clock clock3 = Clock.fixed(Instant.parse("2026-06-25T11:30:00Z"), ZoneId.of("UTC"));
        assertFalse(reservation.canBeCancelled(clock3));
    }

    @Test
    void shouldNotAllowCancellationIfNotPending() {
        // Even if we are 2 hours before start, if status is CONFIRMED, it cannot be cancelled according to the rule:
        // status == ReservationStatus.PENDING
        Instant startTime = Instant.parse("2026-06-25T12:00:00Z");
        Reservation reservation = new Reservation(
            new ReservationId("res-1"),
            new GameId("game-1"),
            new UserId("user-1"),
            ReservationStatus.CONFIRMED,
            startTime,
            Instant.parse("2026-06-25T13:00:00Z"),
            Instant.parse("2026-06-25T10:00:00Z")
        );

        Clock clock = Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneId.of("UTC"));
        assertFalse(reservation.canBeCancelled(clock));
    }

    @Test
    void shouldThrowExceptionWhenEndTimeBeforeStartTime() {
        Instant startTime = Instant.parse("2026-06-25T12:00:00Z");
        Instant invalidEndTime = Instant.parse("2026-06-25T11:59:59Z");

        assertThrows(IllegalArgumentException.class, () -> new Reservation(
            new ReservationId("res-1"),
            new GameId("game-1"),
            new UserId("user-1"),
            ReservationStatus.PENDING,
            startTime,
            invalidEndTime,
            Instant.parse("2026-06-25T10:00:00Z")
        ));
    }

    private Reservation createSampleReservation(ReservationStatus status) {
        return new Reservation(
            new ReservationId("res-1"),
            new GameId("game-1"),
            new UserId("user-1"),
            status,
            Instant.parse("2026-06-25T12:00:00Z"),
            Instant.parse("2026-06-25T13:00:00Z"),
            Instant.parse("2026-06-25T10:00:00Z")
        );
    }
}
