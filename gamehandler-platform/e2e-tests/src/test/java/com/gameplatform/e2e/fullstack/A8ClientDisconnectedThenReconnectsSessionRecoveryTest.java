package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.ChessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A8 — a client that disconnects mid-session causes the local server to abort
 * the session after the heartbeat-timeout threshold; after the client
 * reconnects, a NEW session can be started and completed normally. Central
 * aggregated_statistics ends up with {@code total_aborted_sessions=1} (the
 * aborted session) AND {@code total_sessions=1} (the completed one).
 *
 * <p>The game machine is seeded with {@code building_id='building-2'} so the
 * server's own heartbeat PINGs (published on the <em>game's</em> building id)
 * do not echo back into its {@code building-1} subscription and reset the
 * missed counter; this lets the missed-heartbeat counter climb to the abort
 * threshold deterministically when no client heartbeats arrive.</p>
 */
@DisplayName("A8: client disconnects then reconnects; session recovery end-to-end")
class A8ClientDisconnectedThenReconnectsSessionRecoveryTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-recovery-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess Recovery 1", "building-2", "AVAILABLE");
    }

    private String sessionStatusByGame(String gameId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE game_id=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId);
    }

    private String sessionStatusById(String sessionId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, sessionId);
    }

    private Integer centralStat(String column, String gameType) {
        return centralJdbcTemplate.query(
                "SELECT " + column + " FROM aggregated_statistics WHERE building_id='building-1' AND game_type=?",
                rs -> rs.next() ? rs.getInt(1) : null, gameType);
    }

    @Test
    @DisplayName("disconnect → abort → sync(aborted=1) → reconnect → new session COMPLETED → sync(sessions=1, aborted=1)")
    void clientDisconnectedThenReconnectsSessionRecovery() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        // Start an active session directly through the local service.
        localBean(GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.CHESS,
                        List.of(new UserId("user-1"), new UserId("user-2")), null);

        // Create + connect the client, then disconnect it mid-session.
        TestClientEmulator client = newClient("client-recovery");
        client.disconnect();

        // No heartbeats are sent (the client is gone). Run health checks until
        // the missed-heartbeat counter reaches 3 and the session is aborted.
        await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            for (int i = 0; i < 3; i++) {
                localBean(HealthCheckService.class).performHealthCheck();
            }
            assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("ABORTED");
        });

        // Sync the ABORTED session to central via the real HTTP sync path. The
        // local outbox payload is now read back clean thanks to @Convert on the
        // local OutboxEvent entity, so no manual unwrapping helper is needed.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralStat("total_aborted_sessions", "CHESS")).isEqualTo(1));

        // Reconnect the client.
        client.connect();

        // Start a NEW session and complete it normally with a result.
        GameSession newSession = localBean(GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.CHESS,
                        List.of(new UserId("user-1"), new UserId("user-2")), null);
        String newSessionId = newSession.getId().value();
        localBean(GameSessionService.class).end(new GameSessionId(newSessionId),
                new ChessResult(null, List.of(), "draw", null, WinCondition.DRAW));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(newSessionId)).isEqualTo("COMPLETED"));

        // Sync again. The real sync re-reads all outbox rows (the ABORTED event is
        // re-delivered but deduplicated by eventId; the new COMPLETED event is
        // processed, incrementing total_sessions).
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(centralStat("total_aborted_sessions", "CHESS")).isEqualTo(1);
            assertThat(centralStat("total_sessions", "CHESS")).isEqualTo(1);
        });

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}