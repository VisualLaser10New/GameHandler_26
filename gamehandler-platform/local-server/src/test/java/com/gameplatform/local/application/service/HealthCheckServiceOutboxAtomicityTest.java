package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * R3 (outbox atomicity) — verifies the new contract:
 *
 * <ol>
 *   <li><b>Happy path</b> — {@link SessionAbortHelper#abortAndEmit} atomically
 *       aborts the session, releases the game, and emits a
 *       {@code GAME_SESSION_ABORTED} outbox row in the correct sequence.</li>
 *   <li><b>Outbox-save failure propagates</b> — when
 *       {@code outboxEventRepository.save} throws, the exception propagates
 *       out of {@code abortAndEmit} (NOT swallowed). In a real Spring-managed
 *       invocation this rolls back the ENTIRE REQUIRES_NEW transaction:
 *       session NOT aborted, game NOT released, NO outbox row.</li>
 *   <li><b>ObjectMapper failure propagates</b> — when serialization throws,
 *       the exception propagates and the outbox is NEVER reached.</li>
 *   <li><b>HealthCheckService catches the propagation</b> — the surrounding
 *       {@code try/catch} in {@code performHealthCheck} logs-and-skips a
 *       per-game failure WITHOUT aborting the whole sweep (the class-level
 *       tx is now {@code NEVER}, so there is no sweep tx to mark rollback-only
 *       — the next tick retries the failed game).</li>
 * </ol>
 *
 * <p><b>Rollback semantics caveat (documented gap):</b> the in-memory
 * mutations performed before the failure ({@code session.abort(...)},
 * {@code game.release(...)}) are NOT reversible in a pure Mockito environment
 * — there is no Spring transaction manager to roll them back. Asserting
 * "session.status still IN_PROGRESS after a save failure" requires a real
 * {@code @DataJpaTest} slice. The local-server's {@code @SpringBootApplication}
 * triggers eager {@code MqttConfig.mqttClient} instantiation
 * (connect to {@code tcp://localhost:1883}) during context refresh, which
 * fails in the CI/dev environment (no broker) — see
 * {@code OutboxEventBulkUpdateAtomicityTest} javadoc for the same documented
 * constraint. The propagation contract (the precondition for rollback) is
 * fully verified here; the rollback itself is guaranteed by Spring's
 * REQUIRES_NEW transaction semantics.</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceOutboxAtomicityTest {

    private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock PublishAlertPort publishAlertPort;

    Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    ObjectMapper objectMapper = new ObjectMapper();

    private SessionAbortHelper realHelper;

    @BeforeEach
    void setUp() {
        // Real helper — Mockito mocks its ports. The @Transactional annotation
        // is ignored in this environment (no Spring proxy), so the body runs
        // inline and any exception propagates directly to the caller.
        realHelper = new SessionAbortHelper(
                gameSessionRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                clock,
                objectMapper
        );
    }

    private GameSession inProgressSession(GameId gameId) {
        return new GameSession(
                new GameSessionId("s-1"),
                gameId,
                GameType.CHESS,
                new BuildingId("b-1"),
                GameStatus.IN_PROGRESS,
                NOW.minusSeconds(600),
                null,
                null,
                null,
                null,
                null,
                List.of(new UserId("u-1"))
        );
    }

    private Game inUseGame(GameId gameId) {
        return new Game(gameId, GameType.CHESS, "Chess Table", new BuildingId("b-1"), GameMachineStatus.IN_USE);
    }

    @Test
    void happyPath_abortsSession_releasesGame_emitsOutboxRow() throws Exception {
        GameId gameId = new GameId("g-1");
        GameSession session = inProgressSession(gameId);
        Game game = inUseGame(gameId);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        realHelper.abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT");

        // Session aborted + saved.
        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertNotNull(session.getEndedAt());
        verify(gameSessionRepository).save(session);

        // Game released + saved.
        assertEquals(GameMachineStatus.AVAILABLE, game.getStatus());
        verify(gameRepository).save(game);
        // No Spring tx in mockito env → publishState is invoked inline (not deferred).
        verify(publishGameStatePort).publishState(gameId, GameMachineStatus.AVAILABLE);

        // Outbox row emitted with the expected shape.
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("GAME_SESSION_ABORTED", event.getEventType());
        assertEquals("PENDING", event.getStatus());
        assertTrue(event.getPayload().contains("TIMEOUT"), "payload must carry stopReason=TIMEOUT");
        assertTrue(event.getPayload().contains("s-1"), "payload must carry sessionId=s-1");
    }

    @Test
    void outboxSaveFailure_propagatesFromHelper_notSwallowed() {
        GameId gameId = new GameId("g-1");
        GameSession session = inProgressSession(gameId);
        Game game = inUseGame(gameId);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(outboxEventRepository.save(any())).thenThrow(new RuntimeException("simulated outbox DB failure"));

        // The exception MUST propagate out of abortAndEmit (NOT be swallowed).
        // In a real Spring tx this is what triggers the full REQUIRES_NEW rollback.
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> realHelper.abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT"));
        assertEquals("simulated outbox DB failure", thrown.getMessage());

        // Outbox was attempted exactly once — no retry, no swallow-and-continue.
        verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    void objectMapperFailure_propagatesFromHelper_outboxNeverReached() throws Exception {
        ObjectMapper throwingMapper = mock(ObjectMapper.class);
        when(throwingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialization failed"));
        SessionAbortHelper helperWithThrowingMapper = new SessionAbortHelper(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, clock, throwingMapper);

        GameId gameId = new GameId("g-1");
        GameSession session = inProgressSession(gameId);
        Game game = inUseGame(gameId);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> helperWithThrowingMapper.abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT"));
        assertEquals("serialization failed", thrown.getMessage());

        // Serialization happens BEFORE the outbox save → outbox is never reached.
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void healthCheckService_catchesPropagatedFailure_noExceptionEscapesPerformHealthCheck() {
        // Wire a real helper whose outbox save throws — HealthCheckService's
        // try/catch around abortAndEmit must log-and-skip, NOT propagate.
        HealthCheckService service = new HealthCheckService(
                gameSessionRepository, gameRepository, publishGameStatePort,
                publishAlertPort, clock, realHelper);

        GameId gameId = new GameId("game-1");
        Game game = new Game(gameId, GameType.CHESS, "Chess Table", new BuildingId("b-1"), GameMachineStatus.IN_USE);
        GameSession session = inProgressSession(gameId);

        when(gameRepository.findAll()).thenReturn(List.of(game));
        when(gameSessionRepository.findActiveByGameId(gameId)).thenReturn(Optional.of(session));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(outboxEventRepository.save(any())).thenThrow(new RuntimeException("simulated outbox DB failure"));

        // Three misses → at cycle 3 abortAndEmit throws; HealthCheckService
        // catches and the sweep continues. The class-level tx is NEVER so
        // there is no sweep tx to mark rollback-only — the failure is isolated
        // to this one game.
        assertDoesNotThrow(() -> {
            service.performHealthCheck(); // miss 1
            service.performHealthCheck(); // miss 2
            service.performHealthCheck(); // miss 3 → abort+outbox throws, caught
        });

        // The abort path was entered exactly once (cycle 3).
        verify(gameSessionRepository, times(1)).findActiveByGameId(gameId);
        // The outbox save was attempted exactly once and threw — no retry, no swallow.
        verify(outboxEventRepository, times(1)).save(any());
        // The alert is still published (it runs after the abort try/catch —
        // unreachable detection is independent of session-abort success).
        verify(publishAlertPort).publishAlert(argThat(a -> "UNREACHABLE".equals(a.alertType())));
    }
}