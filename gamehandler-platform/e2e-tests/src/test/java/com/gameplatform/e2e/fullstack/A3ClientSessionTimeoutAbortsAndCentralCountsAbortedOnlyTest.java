package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A3 — a session aborted due to heartbeat timeout flows to central as ABORTED
 * only (total_aborted_sessions increments, total_sessions does not).
 *
 * <p>The game machine is seeded with {@code building_id='building-2'} so the
 * local server's own heartbeat PINGs do not echo back into its subscription
 * (it only subscribes to {@code building-1}), letting the missed-heartbeat
 * counter reach the abort threshold deterministically.</p>
 */
@DisplayName("A3: client session timeout aborts and central counts aborted only")
class A3ClientSessionTimeoutAbortsAndCentralCountsAbortedOnlyTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-darts-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "DARTS", "Darts Board 1", "building-2", "AVAILABLE");
    }

    private String sessionStatusByGame(String gameId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE game_id=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId);
    }

    private Integer localOutboxCount(String eventType) {
        return localJdbcTemplate.query(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type=?",
                rs -> rs.next() ? rs.getInt(1) : 0, eventType);
    }

    private Integer centralStat(String column, String gameType) {
        return centralJdbcTemplate.query(
                "SELECT " + column + " FROM aggregated_statistics WHERE building_id='building-1' AND game_type=?",
                rs -> rs.next() ? rs.getInt(1) : null, gameType);
    }

    @Test
    @DisplayName("heartbeat timeout aborts local session → GAME_SESSION_ABORTED outbox → central total_aborted=1, total_sessions=0")
    void sessionTimeoutAbortsAndCentralCountsAbortedOnly() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        GameSession session = localBean(GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.DARTS,
                        List.of(new UserId("u-1"), new UserId("u-2")), null);

        TestClientEmulator client = newClient("client-darts");

        // Send a few heartbeats so the first health check does NOT abort.
        for (int i = 0; i < 2; i++) {
            client.getHeartbeatPublisher().publishHeartbeat(GAME_ID);
            Thread.sleep(80);
        }
        Thread.sleep(300);
        localBean(HealthCheckService.class).performHealthCheck();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("IN_PROGRESS"));

        // Stop heartbeats; run health checks until the missed counter reaches 3.
        Thread.sleep(400);
        await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            for (int i = 0; i < 3; i++) {
                localBean(HealthCheckService.class).performHealthCheck();
            }
            assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("ABORTED");
        });

        // A GAME_SESSION_ABORTED outbox event must have been produced.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(localOutboxCount("GAME_SESSION_ABORTED")).isGreaterThanOrEqualTo(1));

        // Sync to central via the real HTTP sync path. The local outbox payload
        // is now read back clean thanks to @Convert on the local OutboxEvent
        // entity, so no manual unwrapping helper is needed.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(centralStat("total_aborted_sessions", "DARTS")).isEqualTo(1);
            assertThat(centralStat("total_sessions", "DARTS")).isEqualTo(0);
        });

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}
