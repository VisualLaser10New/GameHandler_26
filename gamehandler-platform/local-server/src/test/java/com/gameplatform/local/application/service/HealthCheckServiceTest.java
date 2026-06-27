package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
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
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock PublishAlertPort publishAlertPort;
    @Mock Clock clock;
    @Mock ObjectMapper objectMapper;

    @InjectMocks HealthCheckService service;

    @BeforeEach
    void stubClock() throws Exception {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
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
    void shouldAbortSessionAfterThreeConsecutiveMisses() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));
        when(gameSessionRepository.findActiveByGameId(gameId)).thenReturn(Optional.of(activeSession(gameId)));
        when(gameRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> abort

        verify(gameSessionRepository).findActiveByGameId(gameId);
        verify(gameSessionRepository).save(any());
        verify(gameRepository).save(any());
        verify(publishGameStatePort).publishState(eq(gameId), eq(GameMachineStatus.AVAILABLE));
        verify(publishAlertPort).publishAlert(argThat(a -> "UNREACHABLE".equals(a.alertType())));
        verify(outboxEventRepository).save(any());
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
    void shouldNotRepeatRecoveryActionsOnFourthMiss() {
        GameId gameId = new GameId("game-1");
        when(gameRepository.findAll()).thenAnswer(i -> List.of(game(gameId, GameMachineStatus.IN_USE)));
        when(gameSessionRepository.findActiveByGameId(gameId)).thenReturn(Optional.of(activeSession(gameId)));
        when(gameRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.performHealthCheck(); // miss 1
        service.performHealthCheck(); // miss 2
        service.performHealthCheck(); // miss 3 -> abort, release, alert
        service.performHealthCheck(); // miss 4 -> no recovery actions executed again

        // Verify recovery actions were executed exactly once (during the 3rd miss)
        verify(gameSessionRepository, times(1)).findActiveByGameId(gameId);
        verify(gameSessionRepository, times(1)).save(any());
        verify(gameRepository, times(1)).save(any());
        verify(publishGameStatePort, times(1)).publishState(eq(gameId), eq(GameMachineStatus.AVAILABLE));
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
