package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock ReservationRepository reservationRepository;

    @InjectMocks StatisticsService service;

    private Game game(GameId id, GameType type) {
        return new Game(id, type, "g", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
    }

    private GameSession completedSession(GameType type) {
        return new GameSession(new GameSessionId("s-1"), new GameId("game-1"), type, new BuildingId("b-1"),
                GameStatus.COMPLETED, Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T10:01:40Z"), 100, new UserId("winner"), WinCondition.WIN, null,
                List.of(new UserId("u-1"), new UserId("winner")));
    }

    @Test
    void shouldComputeStatisticsForGameType() {
        GameId chessId = new GameId("game-1");
        GameId foosballId = new GameId("game-2");
        when(gameRepository.findAll()).thenReturn(List.of(game(chessId, GameType.CHESS), game(foosballId, GameType.FOOSBALL)));
        when(reservationRepository.findByGameId(chessId)).thenReturn(List.of(
                new Reservation(new ReservationId("r1"), chessId, new UserId("u"), ReservationStatus.PENDING, Instant.now(), Instant.now().plusSeconds(60), Instant.now()),
                new Reservation(new ReservationId("r2"), chessId, new UserId("u"), ReservationStatus.PENDING, Instant.now(), Instant.now().plusSeconds(60), Instant.now())));
        when(gameSessionRepository.findByGameType(GameType.CHESS)).thenReturn(List.of(completedSession(GameType.CHESS)));

        LocalStatistics stats = service.getStatistics(GameType.CHESS);

        assertEquals(2, stats.getTotalReservations());
        assertEquals(1, stats.getTotalSessions());
        assertEquals(100.0, stats.getAvgDuration(), 0.001);
    }

    @Test
    void shouldHandleNoGamesOfType() {
        when(gameRepository.findAll()).thenReturn(List.of());
        when(gameSessionRepository.findByGameType(GameType.DARTS)).thenReturn(List.of());
        LocalStatistics stats = service.getStatistics(GameType.DARTS);
        assertEquals(0, stats.getTotalReservations());
        assertEquals(0, stats.getTotalSessions());
    }

    @Test
    void shouldHandleNoSessions() {
        GameId id = new GameId("game-1");
        when(gameRepository.findAll()).thenReturn(List.of(game(id, GameType.CHESS)));
        when(reservationRepository.findByGameId(id)).thenReturn(List.of());
        when(gameSessionRepository.findByGameType(GameType.CHESS)).thenReturn(List.of());

        LocalStatistics stats = service.getStatistics(GameType.CHESS);
        assertEquals(0, stats.getTotalReservations());
        assertEquals(0, stats.getTotalSessions());
        assertEquals(0.0, stats.getAvgDuration(), 0.001);
    }

    @Test
    void shouldReturnActiveSessions() {
        when(gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS)).thenReturn(List.of(completedSession(GameType.CHESS)));
        assertEquals(1, service.getActiveSessions().size());
        verify(gameSessionRepository).findByStatus(GameStatus.IN_PROGRESS);
    }
}
