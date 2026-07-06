package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.exception.SessionAlreadyActiveException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
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
class GameSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock Clock clock;
    @Mock ObjectMapper objectMapper;
    @Mock GameResult result;

    @InjectMocks GameSessionService service;

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(result.getWinnerId()).thenReturn(new UserId("winner"));
        lenient().when(result.getWinCondition()).thenReturn(WinCondition.WIN);
    }

    private Game game(GameMachineStatus status) {
        return new Game(new GameId("game-1"), GameType.CHESS, "Chess 1", new BuildingId("b-1"), status);
    }

    private GameSession session(GameId gameId, GameStatus status) {
        return new GameSession(new GameSessionId("s-1"), gameId, GameType.CHESS, new BuildingId("b-1"),
                status, NOW, null, null, null, null, null, List.of(new UserId("u-1")));
    }

    private Reservation reservation(ReservationId id, GameId gameId, ReservationStatus status, Instant end) {
        Instant start = end.isBefore(NOW.plus(Duration.ofHours(1))) ? end.minus(Duration.ofHours(1)) : NOW.plus(Duration.ofHours(1));
        return new Reservation(id, gameId, new UserId("u-1"), status, start, end, NOW);
    }

    @Test
    void shouldStartSessionWithoutReservation() {
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(gameSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GameSession s = service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), null);

        assertEquals(GameStatus.IN_PROGRESS, s.getStatus());
        verify(gameRepository).save(any());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.IN_USE);
        verify(publishGameStatePort).publishSessionEvent(contains("session/start"), eq(s));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldStartSessionConfirmingReservation() {
        ReservationId rid = new ReservationId("res-1");
        Reservation r = reservation(rid, new GameId("game-1"), ReservationStatus.PENDING, NOW.plus(Duration.ofHours(2)));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.RESERVED)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(r));
        when(gameSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid);

        assertEquals(ReservationStatus.CONFIRMED, r.getStatus());
        verify(reservationRepository).save(r);
    }

    @Test
    void shouldFailStartWhenSessionAlreadyActive() {
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.of(session(new GameId("game-1"), GameStatus.IN_PROGRESS)));
        assertThrows(SessionAlreadyActiveException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), null));
        verify(gameRepository, never()).save(any());
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    void shouldFailStartWhenGameNotFound() {
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(GameNotAvailableException.class, () ->
                service.start(new GameId("nope"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), null));
    }

    @Test
    void shouldFailStartWhenReservationNotFound() {
        ReservationId rid = new ReservationId("res-1");
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ReservationNotFoundException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void shouldFailStartWhenReservationExpired() {
        ReservationId rid = new ReservationId("res-1");
        Reservation r = reservation(rid, new GameId("game-1"), ReservationStatus.EXPIRED, NOW.plus(Duration.ofHours(2)));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(r));
        assertThrows(ReservationExpiredException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid));
    }

    @Test
    void shouldFailStartWhenReservationEndTimePassed() {
        ReservationId rid = new ReservationId("res-1");
        Reservation r = reservation(rid, new GameId("game-1"), ReservationStatus.PENDING, NOW.minus(Duration.ofHours(1)));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(r));
        assertThrows(ReservationExpiredException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid));
    }

    @Test
    void shouldFailStartWhenGameIsInUseButNoActiveSession() {
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.IN_USE)));
        assertThrows(InvalidGameStateTransitionException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), null));
        verify(gameSessionRepository, never()).save(any());
    }

    @Test
    void shouldFailStartWhenReservationGameIdMismatches() {
        ReservationId rid = new ReservationId("res-1");
        Reservation r = reservation(rid, new GameId("game-2"), ReservationStatus.PENDING, NOW.plus(Duration.ofHours(2)));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(r));

        assertThrows(InvalidGameStateTransitionException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid));
    }

    @Test
    void shouldFailStartWhenReservationCancelled() {
        ReservationId rid = new ReservationId("res-1");
        Reservation r = reservation(rid, new GameId("game-1"), ReservationStatus.CANCELLED, NOW.plus(Duration.ofHours(2)));
        when(gameSessionRepository.findActiveByGameId(any())).thenReturn(Optional.empty());
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));
        when(reservationRepository.findById(any())).thenReturn(Optional.of(r));

        assertThrows(ReservationExpiredException.class, () ->
                service.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u-1"), new UserId("u-2")), rid));
    }

    @Test
    void shouldEndSessionAndReleaseGame() throws Exception {
        GameSession s = session(new GameId("game-1"), GameStatus.IN_PROGRESS);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.IN_USE)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.end(new GameSessionId("s-1"), result);

        assertEquals(GameStatus.COMPLETED, s.getStatus());
        verify(gameSessionRepository).save(s);
        verify(gameRepository).save(any());
        verify(publishGameStatePort).publishState(new GameId("game-1"), GameMachineStatus.AVAILABLE);
        verify(publishGameStatePort).publishSessionEvent(contains("session/end"), eq(s));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void shouldReturnEarlyWhenEndingAlreadyCompletedSession() {
        GameSession s = session(new GameId("game-1"), GameStatus.COMPLETED);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        service.end(new GameSessionId("s-1"), result);
        verify(gameSessionRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldEndAbortedSessionWithoutReleasingGame() throws Exception {
        GameSession s = session(new GameId("game-1"), GameStatus.ABORTED);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.AVAILABLE)));

        service.end(new GameSessionId("s-1"), result);

        assertEquals(GameStatus.COMPLETED, s.getStatus());
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
        // S1: a session that was already ABORTED must not emit a second GAME_SESSION_COMPLETED
        // outbox event (its central-stats contribution was already made via GAME_SESSION_ABORTED).
        verify(outboxEventRepository, never()).save(any());
        verify(publishGameStatePort).publishSessionEvent(contains("session/end"), eq(s));
    }

    @Test
    void shouldFailEndWhenSessionNotFound() {
        when(gameSessionRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.end(new GameSessionId("nope"), result));
    }

    @Test
    void shouldFailEndWhenGameNotFound() {
        GameSession s = session(new GameId("game-1"), GameStatus.IN_PROGRESS);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(GameNotAvailableException.class, () -> service.end(new GameSessionId("s-1"), result));
    }

    @Test
    void shouldPauseInProgressSession() {
        GameSession s = session(new GameId("game-1"), GameStatus.IN_PROGRESS);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.IN_USE)));

        service.pause(new GameSessionId("s-1"));

        assertEquals(GameStatus.PAUSED, s.getStatus());
        verify(gameSessionRepository).save(s);
        verify(publishGameStatePort).publishSessionEvent(contains("session/pause"), eq(s));
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldFailPauseWhenSessionNotInProgress() {
        GameSession s = session(new GameId("game-1"), GameStatus.PAUSED);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        assertThrows(InvalidGameStateTransitionException.class, () -> service.pause(new GameSessionId("s-1")));
    }

    @Test
    void shouldResumePausedSession() {
        GameSession s = session(new GameId("game-1"), GameStatus.PAUSED);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        when(gameRepository.findById(any())).thenReturn(Optional.of(game(GameMachineStatus.IN_USE)));

        service.resume(new GameSessionId("s-1"));

        assertEquals(GameStatus.IN_PROGRESS, s.getStatus());
        verify(publishGameStatePort).publishSessionEvent(contains("session/resume"), eq(s));
    }

    @Test
    void shouldFailResumeWhenSessionNotPaused() {
        GameSession s = session(new GameId("game-1"), GameStatus.IN_PROGRESS);
        when(gameSessionRepository.findById(any())).thenReturn(Optional.of(s));
        assertThrows(InvalidGameStateTransitionException.class, () -> service.resume(new GameSessionId("s-1")));
    }
}
