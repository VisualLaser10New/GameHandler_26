package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock ReservationRepository reservationRepository;
    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;

    @InjectMocks ReservationExpirationService service;

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    private Game reservedGame() {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), GameMachineStatus.RESERVED);
    }

    @Test
    void shouldExpirePendingReservationAndReleaseGame() {
        Reservation r = new Reservation(new ReservationId("res-1"), new GameId("game-1"), new UserId("u-1"),
                ReservationStatus.PENDING, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(3)));
        when(reservationRepository.findExpired(any())).thenReturn(List.of(r));
        when(gameRepository.findById(any())).thenReturn(Optional.of(reservedGame()));

        service.expireReservations();

        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        verify(reservationRepository).save(r);
        verify(gameRepository).save(any());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
    }

    @Test
    void shouldDoNothingWhenNoExpiredReservations() {
        when(reservationRepository.findExpired(any())).thenReturn(List.of());
        service.expireReservations();
        verify(gameRepository, never()).findById(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldSkipGameReleaseWhenGameMissingButStillExpireReservation() {
        Reservation r = new Reservation(new ReservationId("res-1"), new GameId("game-1"), new UserId("u-1"),
                ReservationStatus.PENDING, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), NOW);
        when(reservationRepository.findExpired(any())).thenReturn(List.of(r));
        when(gameRepository.findById(any())).thenReturn(Optional.empty());

        service.expireReservations();

        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        verify(reservationRepository).save(r);
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldExpireMultipleReservationsInOneCycle() {
        Reservation r1 = new Reservation(new ReservationId("res-1"), new GameId("game-1"), new UserId("u-1"),
                ReservationStatus.PENDING, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), NOW);
        Reservation r2 = new Reservation(new ReservationId("res-2"), new GameId("game-2"), new UserId("u-2"),
                ReservationStatus.PENDING, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), NOW);
        when(reservationRepository.findExpired(any())).thenReturn(List.of(r1, r2));
        when(gameRepository.findById(any())).thenReturn(Optional.of(reservedGame()));

        service.expireReservations();

        verify(reservationRepository, times(2)).save(any());
        verify(publishGameStatePort, times(2)).publishState(any(), any());
    }
}
