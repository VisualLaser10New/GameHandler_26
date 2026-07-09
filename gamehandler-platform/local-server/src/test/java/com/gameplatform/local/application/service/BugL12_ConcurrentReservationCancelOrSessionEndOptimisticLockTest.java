package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Bug L-12: POF-5 optimistic locking on reservation cancellation.
 * {@code ReservationService.cancel(...)} cancels the reservation
 * ({@code reservation.cancel()} then {@code reservationRepository.save(reservation)}).
 * When a concurrent request loses the optimistic lock on that save, the adapter
 * throws {@link ConcurrentStateException}; the service must propagate it
 * unchanged and must NOT leak a Spring exception type.
 */
@ExtendWith(MockitoExtension.class)
class BugL12_ConcurrentReservationCancelOrSessionEndOptimisticLockTest {

    @Mock ReservationRepository reservationRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;

    private ReservationService service;

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final ReservationId RESERVATION_ID = new ReservationId("res-1");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final UserId USER_ID = new UserId("user-1");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ReservationService(
                reservationRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                clock,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("BUG L-12: a concurrent reservation cancel that loses the optimistic lock propagates ConcurrentStateException (not a Spring type)")
    void concurrentCancelPropagatesConcurrentStateException() {
        // start time more than 1h in the future so canBeCancelled(clock) is true.
        Reservation reservation = new Reservation(
                RESERVATION_ID, GAME_ID, USER_ID, ReservationStatus.PENDING,
                NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)), NOW);

        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        // The reservation save (PENDING -> CANCELLED) loses the optimistic lock
        // and the adapter raises ConcurrentStateException; stub the port.
        when(reservationRepository.save(any()))
                .thenThrow(new ConcurrentStateException("Concurrent modification of reservation res-1"));

        assertThrows(ConcurrentStateException.class, () -> service.cancel(RESERVATION_ID));
    }
}