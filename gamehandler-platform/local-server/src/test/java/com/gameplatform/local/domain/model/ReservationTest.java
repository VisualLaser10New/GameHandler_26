package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant START = Instant.parse("2026-06-25T12:00:00Z");
    private static final Instant END = Instant.parse("2026-06-25T13:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-06-25T10:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private Reservation sample(ReservationStatus status) {
        return new Reservation(new ReservationId("res-1"), new GameId("game-1"),
                new UserId("user-1"), status, START, END, CREATED);
    }

    @Nested
    class Construction {

        @Test
        void shouldCreateReservationSuccessfully() {
            ReservationId id = new ReservationId("res-1");
            GameId gameId = new GameId("game-1");
            UserId userId = new UserId("user-1");

            Reservation reservation = new Reservation(id, gameId, userId, ReservationStatus.PENDING,
                    START, END, CREATED);

            assertThat(reservation.getId()).isEqualTo(id);
            assertThat(reservation.getGameId()).isEqualTo(gameId);
            assertThat(reservation.getUserId()).isEqualTo(userId);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(reservation.getStartTime()).isEqualTo(START);
            assertThat(reservation.getEndTime()).isEqualTo(END);
            assertThat(reservation.getCreatedAt()).isEqualTo(CREATED);
        }

        @Test
        void shouldAllowZeroLengthReservationWhenStartEqualsEnd() {
            assertThat(new Reservation(new ReservationId("r"), new GameId("g"), new UserId("u"),
                    ReservationStatus.PENDING, START, START, CREATED)).isNotNull();
        }

        @Test
        void shouldRejectEndTimeStrictlyBeforeStartTime() {
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), ReservationStatus.PENDING, START,
                    START.minusSeconds(1), CREATED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EndTime cannot be before StartTime");
        }

        @Test
        void shouldRejectEndTimeFarBeforeStartTime() {
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), ReservationStatus.PENDING, END, START, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectAnyNullRequiredField() {
            assertThatThrownBy(() -> new Reservation(null, new GameId("g"), new UserId("u"),
                    ReservationStatus.PENDING, START, END, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), null, new UserId("u"),
                    ReservationStatus.PENDING, START, END, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"), null,
                    ReservationStatus.PENDING, START, END, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), null, START, END, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), ReservationStatus.PENDING, null, END, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), ReservationStatus.PENDING, START, null, CREATED))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Reservation(new ReservationId("r"), new GameId("g"),
                    new UserId("u"), ReservationStatus.PENDING, START, END, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNullViaValueObjectsEagerly() {
            assertThatThrownBy(() -> new ReservationId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ReservationId("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class StateTransitions {

        @Test
        void shouldConfirmPendingReservation() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.confirm();
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldCancelPendingReservation() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.cancel();
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void shouldFailToExpireConfirmedReservation() {
            Reservation r = sample(ReservationStatus.CONFIRMED);
            assertThatThrownBy(r::expire)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void shouldExpirePendingReservation() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.expire();
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        }

        @Test
        void confirmShouldFailOnCancelledOrExpiredState() {
            Reservation cancelled = sample(ReservationStatus.CANCELLED);
            assertThatThrownBy(cancelled::confirm)
                    .isInstanceOf(InvalidGameStateTransitionException.class);

            Reservation expired = sample(ReservationStatus.EXPIRED);
            assertThatThrownBy(expired::confirm)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void cancelShouldFailOnConfirmedReservation() {
            Reservation confirmed = sample(ReservationStatus.CONFIRMED);
            assertThatThrownBy(confirmed::cancel)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void cancelShouldFailOnAlreadyCancelledReservation() {
            Reservation cancelled = sample(ReservationStatus.CANCELLED);
            assertThatThrownBy(cancelled::cancel)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void expireShouldFailOnAlreadyCancelledReservation() {
            Reservation cancelled = sample(ReservationStatus.CANCELLED);
            assertThatThrownBy(cancelled::expire)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void confirmIsPreventedIfAlreadyConfirmed() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.confirm();
            assertThatThrownBy(r::confirm)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void fullHappyPathPendingConfirmedCancelledThrows() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.confirm();
            assertThatThrownBy(r::cancel)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void fullHappyPathPendingConfirmedExpiredThrows() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.confirm();
            assertThatThrownBy(r::expire)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }

        @Test
        void confirmCancelReconfirmThrows() {
            Reservation r = sample(ReservationStatus.PENDING);
            r.confirm();
            assertThatThrownBy(r::cancel)
                    .isInstanceOf(InvalidGameStateTransitionException.class);
        }
    }

    @Nested
    class CancellationPolicy {

        @Test
        void shouldBeCancellableWhenPendingAndMoreThanOneHourBeforeStart() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock oneHourAndOneSecondBeforeStart = Clock.fixed(START.minusSeconds(3601), UTC);
            assertThat(r.canBeCancelled(oneHourAndOneSecondBeforeStart)).isTrue();
        }

        @Test
        void shouldNotBeCancellableExactlyOneHourBeforeStart() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock exactlyOneHourBeforeStart = Clock.fixed(START.minusSeconds(3600), UTC);
            assertThat(r.canBeCancelled(exactlyOneHourBeforeStart)).isFalse();
        }

        @Test
        void shouldNotBeCancellableLessThanOneHourBeforeStart() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock thirtyMinutesBeforeStart = Clock.fixed(START.minusSeconds(1800), UTC);
            assertThat(r.canBeCancelled(thirtyMinutesBeforeStart)).isFalse();
        }

        @Test
        void shouldNotBeCancellableWhenStartTimeIsInThePast() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock afterStart = Clock.fixed(START.plusSeconds(60), UTC);
            assertThat(r.canBeCancelled(afterStart)).isFalse();
        }

        @Test
        void shouldNotBeCancellableWhenNotPendingEvenIfMoreThanOneHourBeforeStart() {
            Reservation confirmed = sample(ReservationStatus.CONFIRMED);
            Clock twoHoursBeforeStart = Clock.fixed(START.minusSeconds(7200), UTC);
            assertThat(confirmed.canBeCancelled(twoHoursBeforeStart)).isFalse();

            Reservation cancelled = sample(ReservationStatus.CANCELLED);
            assertThat(cancelled.canBeCancelled(twoHoursBeforeStart)).isFalse();

            Reservation expired = sample(ReservationStatus.EXPIRED);
            assertThat(expired.canBeCancelled(twoHoursBeforeStart)).isFalse();
        }

        @Test
        void shouldRejectNullClock() {
            Reservation r = sample(ReservationStatus.PENDING);
            assertThatThrownBy(() -> r.canBeCancelled(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Clock cannot be null");
        }

        @Test
        void shouldBeCancellableFarInTheFutureBeforeStart() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock oneYearBeforeStart = Clock.fixed(START.minus(Duration.ofDays(365)), UTC);
            assertThat(r.canBeCancelled(oneYearBeforeStart)).isTrue();
        }

        @Test
        void shouldBeCancellableOneSecondAfterOneHourThreshold() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock clock = Clock.fixed(START.minusSeconds(3601), UTC);
            assertThat(r.canBeCancelled(clock)).isTrue();
        }

        @Test
        void shouldNotBeCancellableAtExactlyStartTime() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock atStart = Clock.fixed(START, UTC);
            assertThat(r.canBeCancelled(atStart)).isFalse();
        }

        @Test
        void shouldNotBeCancellableWayAfterStart() {
            Reservation r = sample(ReservationStatus.PENDING);
            Clock farAfter = Clock.fixed(START.plus(Duration.ofDays(1)), UTC);
            assertThat(r.canBeCancelled(farAfter)).isFalse();
        }
    }

    @Nested
    class Equality {

        @Test
        void reservationsDoNotOverrideEqualsSoIdentityEqualityHolds() {
            Reservation a = sample(ReservationStatus.PENDING);
            Reservation b = sample(ReservationStatus.PENDING);
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
            assertThat(a.equals("not a reservation")).isFalse();
        }

        @Test
        void sameHashCodeAcrossInvocationsButNotContractuallyBound() {
            Reservation r = sample(ReservationStatus.PENDING);
            assertThat(r.hashCode()).isEqualTo(r.hashCode());
        }
    }
}
