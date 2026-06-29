package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug L-01: HealthCheckService uses {@code if (missed == 3)} instead of {@code if (missed >= 3)}.
 *
 * <p>After the counter passes 3 (because the first active session was aborted at cycle 3),
 * the missed counter continues incrementing (4, 5, 6...). If a NEW session starts on the
 * same machine without any heartbeat response to reset the counter, the abort logic at
 * {@code == 3} never fires again, so the new session is never aborted.</p>
 */
class BugL01_HealthCheckEqualsThreeTest {

    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private GameRepository gameRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PublishGameStatePort publishGameStatePort;
    @Mock private PublishAlertPort publishAlertPort;

    private Clock clock;
    private ObjectMapper objectMapper;
    private HealthCheckService healthCheckService;

    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("building-1");
    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T08:00:00Z");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        objectMapper = new ObjectMapper();
        healthCheckService = new HealthCheckService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, publishAlertPort, clock, objectMapper
        );
    }

    @Test
    @DisplayName("BUG L-01: After missed counter passes 3, a new session on the same machine is never aborted because '== 3' never matches again")
    void newSessionNotAbortedWhenMissedCounterExceedsThree() {
        // -- Setup the game machine
        Game game = new Game(GAME_ID, GameType.CHESS, "Chess Table 1", BUILDING_ID, GameMachineStatus.IN_USE);
        when(gameRepository.findAll()).thenReturn(List.of(game));

        // -- Session #1: active session that will be aborted at cycle 3
        GameSession session1 = new GameSession(
                new GameSessionId("session-1"), GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, FIXED_NOW.minusSeconds(600),
                null, null, null, null, null, List.of(new UserId("user-1"))
        );

        // For cycles 1-3, session1 is the active session
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.of(session1));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // -- Run 3 health check cycles WITHOUT any heartbeat response
        // Cycle 1: missed = 1 (no abort)
        healthCheckService.performHealthCheck();
        // Cycle 2: missed = 2 (no abort)
        healthCheckService.performHealthCheck();
        // Cycle 3: missed = 3 → session1 IS aborted (== 3 triggers)
        healthCheckService.performHealthCheck();

        // Verify session1 was aborted at cycle 3
        verify(gameSessionRepository, times(1)).save(session1);
        assertEquals(GameStatus.ABORTED, session1.getStatus(),
                "Session #1 should have been aborted at missed == 3");

        // -- Now simulate: NO heartbeat response arrives (counter is NOT reset),
        // -- but a NEW session #2 starts on the same machine.
        // -- The missed counter is at 3 and keeps incrementing: 4, 5, 6...
        GameSession session2 = new GameSession(
                new GameSessionId("session-2"), GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, FIXED_NOW,
                null, null, null, null, null, List.of(new UserId("user-2"))
        );

        // Now session2 is the active session
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.of(session2));

        // Reset the game to IN_USE for session 2
        Game game2 = new Game(GAME_ID, GameType.CHESS, "Chess Table 1", BUILDING_ID, GameMachineStatus.IN_USE);
        when(gameRepository.findAll()).thenReturn(List.of(game2));

        // Reset mock counters to track session2 saves
        reset(gameSessionRepository);
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.of(session2));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // -- Run 3 MORE cycles without response (cycles 4, 5, 6)
        // missed goes from 4 → 5 → 6. The condition == 3 never fires again.
        healthCheckService.performHealthCheck(); // missed = 4
        healthCheckService.performHealthCheck(); // missed = 5
        healthCheckService.performHealthCheck(); // missed = 6

        // Under the fixed behavior, session2 IS aborted on cycle 6 because the missed counter
        // was reset to 0 after cycle 3, and then reached 3 again at cycle 6.
        assertEquals(GameStatus.ABORTED, session2.getStatus(),
                "Session #2 should have been aborted because missed counter reached >= 3 at cycle 6.");

        // Additional verification: gameSessionRepository.save(session2) was called with
        // an ABORTED session2
        verify(gameSessionRepository, times(1)).save(argThat(s -> s.getStatus() == GameStatus.ABORTED));
    }
}
