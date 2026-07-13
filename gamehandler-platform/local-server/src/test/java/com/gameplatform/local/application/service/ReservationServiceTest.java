package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
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
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock ReservationRepository reservationRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ObjectMapper objectMapper;
    @Mock Clock clock;

    @InjectMocks ReservationService service;

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    private Game availableGame() {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
    }

    private Reservation pendingReservation(ReservationId id, Instant start, Instant end) {
        return new Reservation(id, new GameId("game-1"), new UserId("user-1"), ReservationStatus.PENDING, start, end, NOW);
    }

    @Test
    void shouldCreateReservationSuccessfully() throws Exception {
        Game game = availableGame();
        when(gameRepository.findById(any())).thenReturn(Optional.of(game));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Reservation result = service.create(new GameId("game-1"), new UserId("user-1"),
                NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)));

        assertEquals(ReservationStatus.PENDING, result.getStatus());
        assertEquals(GameMachineStatus.RESERVED, game.getStatus());
        verify(gameRepository).save(game);
        verify(reservationRepository).save(any());
        verify(outboxEventRepository).save(any());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.RESERVED);
    }

    @Test
    void shouldFailCreateWhenGameNotFound() {
        when(gameRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(GameNotAvailableException.class, () ->
                service.create(new GameId("game-1"), new UserId("user-1"), NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2))));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldFailCreateWhenEndIsInThePast() {
        when(gameRepository.findById(any())).thenReturn(Optional.of(availableGame()));
        assertThrows(ReservationExpiredException.class, () ->
                service.create(new GameId("game-1"), new UserId("user-1"), NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1))));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void shouldFailCreateWhenGameNotAvailable() {
        Game reserved = new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), GameMachineStatus.RESERVED);
        when(gameRepository.findById(any())).thenReturn(Optional.of(reserved));
        assertThrows(InvalidGameStateTransitionException.class, () ->
                service.create(new GameId("game-1"), new UserId("user-1"), NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2))));
        verify(reservationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldWrapSerializationFailureAsRuntimeException() throws Exception {
        when(gameRepository.findById(any())).thenReturn(Optional.of(availableGame()));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fail") {});

        assertThrows(RuntimeException.class, () ->
                service.create(new GameId("game-1"), new UserId("user-1"), NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2))));
        verify(outboxEventRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldCancelCancellableReservation() throws Exception {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));
        Game reserved = new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), GameMachineStatus.RESERVED);
        when(gameRepository.findById(any())).thenReturn(Optional.of(reserved));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.cancel(id);

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, reserved.getStatus());
        verify(reservationRepository).save(reservation);
        verify(gameRepository).save(reserved);
        verify(outboxEventRepository).save(any());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
    }

    @Test
    void shouldFailCancelWhenReservationNotFound() {
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ReservationNotFoundException.class, () -> service.cancel(new ReservationId("nope")));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldFailCancelWhenReservationExpired() {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        reservation.expire();
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));

        assertThrows(ReservationExpiredException.class, () -> service.cancel(id));
        verify(reservationRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
    }

    @Test
    void shouldFailCancelWhenWithinOneHourOfStart() {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofMinutes(30)), NOW.plus(Duration.ofHours(1)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));

        assertThrows(IllegalStateException.class, () -> service.cancel(id));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldFailCancelWhenGameNotFoundAfterCancellation() {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));
        when(gameRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(GameNotAvailableException.class, () -> service.cancel(id));
        // Without Spring TX, the reservation was already persisted (non-atomic).
        verify(reservationRepository).save(reservation);
        verify(gameRepository, never()).save(any());
    }

    @Test
    void shouldDelegateGetByUser() {
        UserId userId = new UserId("u-1");
        when(reservationRepository.findByUserId(userId)).thenReturn(List.of());
        service.getByUser(userId);
        verify(reservationRepository).findByUserId(userId);
    }

    @Test
    void shouldDelegateGetByGame() {
        GameId gameId = new GameId("game-1");
        when(reservationRepository.findByGameId(gameId)).thenReturn(List.of());
        service.getByGame(gameId);
        verify(reservationRepository).findByGameId(gameId);
    }

    @Test
    void cancelForUser_throwsAccessDenied_whenActingUserIsNotOwner() {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.cancel(id, new UserId("someone-else")));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelForUser_succeeds_whenActingUserIsOwner() throws Exception {
        ReservationId id = new ReservationId("res-1");
        Reservation reservation = pendingReservation(id, NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(reservation));
        when(gameRepository.findById(any())).thenReturn(Optional.of(availableGame()));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.cancel(id, new UserId("user-1"));
        verify(reservationRepository).save(any());
    }
}
