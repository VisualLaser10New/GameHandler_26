package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug L-01: HealthCheckService uses {@code if (missed == 3)} instead of {@code if (missed >= 3)}.
 *
 * <p>After the counter passes 3 (because the first active session was aborted at cycle 3),
 * the missed counter continues incrementing (4, 5, 6...). If a NEW session starts on the
 * same machine without any heartbeat response to reset the counter, the abort logic at
 * {@code == 3} never fires again, so the new session is never aborted.</p>
 *
 * <p><b>R3 note:</b> the per-game abort now lives in {@link SessionAbortHelper#abortAndEmit}
 * (separate bean, REQUIRES_NEW tx). The helper is mocked here so the test asserts the
 * delegation contract — {@code abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT")} is
 * invoked exactly once per abort cycle — rather than asserting the in-memory session
 * status transition that happens inside the helper (those are covered by
 * {@code SessionAbortHelperTest}).</p>
 */
class BugL01_HealthCheckEqualsThreeTest {

    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private GameRepository gameRepository;
    @Mock private PublishGameStatePort publishGameStatePort;
    @Mock private PublishAlertPort publishAlertPort;
    @Mock private SessionAbortHelper sessionAbortHelper;

    private Clock clock;
    private HealthCheckService healthCheckService;

    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("building-1");
    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T08:00:00Z");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        healthCheckService = new HealthCheckService(
                gameSessionRepository, gameRepository, publishGameStatePort,
                publishAlertPort, clock, sessionAbortHelper
        );
    }

    @Test
    @DisplayName("BUG L-01: After missed counter passes 3, a new session on the same machine is never aborted because '== 3' never matches again")
    void newSessionNotAbortedWhenMissedCounterExceedsThree() throws Exception {
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

        // -- Run 3 health check cycles WITHOUT any heartbeat response
        // Cycle 1: missed = 1 (no abort)
        healthCheckService.performHealthCheck();
        // Cycle 2: missed = 2 (no abort)
        healthCheckService.performHealthCheck();
        // Cycle 3: missed = 3 → session1 abort path fires (>= 3)
        healthCheckService.performHealthCheck();

        // Verify session1's abort+outbox was delegated to the helper exactly once at cycle 3.
        verify(sessionAbortHelper, times(1)).abortAndEmit(eq(session1), eq(StopReason.TIMEOUT), eq("TIMEOUT"));

        // -- Now simulate: NO heartbeat response arrives (counter is NOT reset),
        // -- but a NEW session #2 starts on the same machine.
        // -- The missed counter is at 3 → reset to 0 at cycle 3 → then 4→5→6 again.
        GameSession session2 = new GameSession(
                new GameSessionId("session-2"), GAME_ID, GameType.CHESS, BUILDING_ID,
                GameStatus.IN_PROGRESS, FIXED_NOW,
                null, null, null, null, null, List.of(new UserId("user-2"))
        );

        // Now session2 is the active session
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.of(session2));

        // Reset the helper mock to track session2's abort separately.
        reset(sessionAbortHelper);

        // -- Run 3 MORE cycles without response (cycles 4, 5, 6)
        // missed goes 4 → 5 → 6. With the fixed >= 3 condition the counter resets
        // (missed=3 at cycle 6 → abortAndEmit fires again).
        healthCheckService.performHealthCheck(); // missed = 4
        healthCheckService.performHealthCheck(); // missed = 5
        healthCheckService.performHealthCheck(); // missed = 6 → abort

        // Under the fixed >= 3 behaviour, session2's abort+outbox is delegated to
        // the helper at cycle 6 (the counter reached 3 again after the cycle-3 reset).
        verify(sessionAbortHelper, times(1)).abortAndEmit(eq(session2), eq(StopReason.TIMEOUT), eq("TIMEOUT"));
    }
}