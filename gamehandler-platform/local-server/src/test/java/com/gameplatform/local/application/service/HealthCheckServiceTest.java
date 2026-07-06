package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.*;
import java.time.Clock;
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
class HealthCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock PublishAlertPort publishAlertPort;
    @Mock Clock clock;
    @Mock SessionAbortHelper sessionAbortHelper;

    @InjectMocks HealthCheckService service;

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    private Game game(GameId id, GameMachineStatus status) {
        return new Game(id, GameType.CHESS, "g", new BuildingId("b-1"), status);
    }

    private GameSession activeSession(GameId gameId) {
        return new GameSession(new GameSessionId("s-1"), gameId, GameType.CHESS, new BuildingId("b-1"),
                GameStatus.IN_PROGRESS, NOW, null, null, null, null, null, List.of(new UserId("u-1")));
    }

    @Test
    void shouldDoNothingWhenNoGames() {
        when(gameRepository.findAll()).thenReturn(List.of());
        service.performHealthCheck();
        verify(publishGameStatePort, never()).publishSessionEvent(anyString(), any());
        verify(publishAlertPort, never()).publishAlert(any());
    }

    @Test
    void shouldResetMissedCounterWhenClientResponded() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));
        service.registerHeartbeat(gameId);

        service.performHealthCheck();

        verify(publishAlertPort, never()).publishAlert(any());
        verify(gameSessionRepository, never()).findActiveByGameId(any());
        verify(publishGameStatePort).publishSessionEvent(contains("heartbeat"), eq("PING"));
    }

    @Test
    void shouldIncrementMissedCountButNotAbortOnFirstMiss() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));

        service.performHealthCheck();

        verify(publishAlertPort, never()).publishAlert(any());
        verify(gameSessionRepository, never()).findActiveByGameId(any());
    }

    @Test
    void shouldAbortSessionAfterThreeConsecutiveMisses() throws Exception {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));
        when(gameSessionRepository.findActiveByGameId(gameId)).thenReturn(Optional.of(activeSession(gameId)));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> abort

        // The abort + game release + outbox emission now run atomically inside
        // SessionAbortHelper.abortAndEmit (separate bean, REQUIRES_NEW tx). The
        // HealthCheckService merely delegates — verifying the delegation is the
        // contract; the helper itself is covered by SessionAbortHelperTest.
        verify(gameSessionRepository).findActiveByGameId(gameId);
        verify(sessionAbortHelper).abortAndEmit(any(GameSession.class), eq(StopReason.TIMEOUT), eq("TIMEOUT"));
        verify(publishAlertPort).publishAlert(argThat(a -> "UNREACHABLE".equals(a.alertType())));
    }

    @Test
    void shouldPublishAlertButNotReleaseWhenGameAlreadyAvailable() {
        GameId gameId = new GameId("game-1");
        // Pre-accumulate 2 misses by running twice with AVAILABLE game.
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.AVAILABLE)));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> alert but no release

        verify(publishAlertPort).publishAlert(any());
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldNotRepeatRecoveryActionsOnFourthMiss() throws Exception {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));
        when(gameSessionRepository.findActiveByGameId(gameId)).thenReturn(Optional.of(activeSession(gameId)));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> abort, release, alert
        service.performHealthCheck(); // miss 4 -> no recovery actions executed again

        // Verify recovery actions were executed exactly once (during the 3rd miss)
        verify(gameSessionRepository, times(1)).findActiveByGameId(gameId);
        verify(sessionAbortHelper, times(1)).abortAndEmit(any(GameSession.class), eq(StopReason.TIMEOUT), eq("TIMEOUT"));
        verify(publishAlertPort, times(1)).publishAlert(argThat(a -> "UNREACHABLE".equals(a.alertType())));
    }

    @Test
    void shouldNotReleaseWhenGameIsReserved() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.RESERVED)));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> alert but no release

        verify(publishAlertPort).publishAlert(any());
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }

    @Test
    void shouldNotReleaseWhenGameIsMaintenance() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.MAINTENANCE)));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> alert but no release

        verify(publishAlertPort).publishAlert(any());
        verify(gameRepository, never()).save(any());
        verify(publishGameStatePort, never()).publishState(any(), any());
    }
}