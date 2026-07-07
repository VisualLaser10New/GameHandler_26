package com.gameplatform.e2e.fullstack;

import com.gameplatform.central.application.service.LocalServerHealthMonitorService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A1 — client heartbeats keep the building active at central, and stopping
 * heartbeats causes the local session to be aborted and the central building
 * to be deactivated.
 *
 * <p>The local game machine is seeded with {@code building_id='building-2'} so
 * that the heartbeat PINGs published by {@link HealthCheckService} (which use
 * the game's building id in the topic) are emitted on
 * {@code building/building-2/...}; the local server is only subscribed to
 * {@code building-1} topics, so it never receives its own PING echo and the
 * missed-heartbeat counter climbs deterministically to the abort threshold.
 * The client emulator still publishes its heartbeats on {@code building-1},
 * which the local server receives and registers.</p>
 */
@DisplayName("A1: client heartbeat keeps building active; stop → abort + central deactivation")
class A1ClientHeartbeatKeepsBuildingActiveTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-hb-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess HB", "building-2", "AVAILABLE");
    }

    private String sessionStatusByGame(String gameId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE game_id=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId);
    }

    private boolean buildingActive(String buildingId) {
        return localServerRegistryPort.findAll().stream()
                .filter(s -> s.getBuildingId().id().equals(buildingId))
                .findFirst()
                .map(RegisteredLocalServer::isActive)
                .orElse(false);
    }

    @Test
    @DisplayName("heartbeats keep the session alive and the building active; stopping heartbeats aborts and deactivates")
    void heartbeatKeepsActiveThenStopDeactivates() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        // Start an active session directly through the local service.
        localBean(com.gameplatform.local.application.service.GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.CHESS,
                        List.of(new UserId("u-a"), new UserId("u-b")), null);

        TestClientEmulator client = newClient("client-hb");

        // Publish a few client heartbeats → local registers them (responded=true).
        for (int i = 0; i < 3; i++) {
            client.getHeartbeatPublisher().publishHeartbeat(GAME_ID);
            Thread.sleep(80);
        }
        Thread.sleep(300);

        // performHealthCheck while the client is alive: must NOT abort.
        localBean(HealthCheckService.class).performHealthCheck();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("IN_PROGRESS"));

        // Central registration keeps the building active.
        assertThat(buildingActive("building-1"))
                .as("building-1 must be active right after registration")
                .isTrue();

        // STOP heartbeats. Let echoes settle, then run several health checks so
        // the missed counter reaches the abort threshold of 3.
        Thread.sleep(400);
        await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            for (int i = 0; i < 3; i++) {
                localBean(HealthCheckService.class).performHealthCheck();
            }
            assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("ABORTED");
        });

        // Manually age the central last_seen_at past the staleness threshold and
        // run the central monitor to deactivate the building.
        centralJdbcTemplate.update(
                "UPDATE local_servers SET last_seen_at = ? WHERE building_id = ?",
                Timestamp.from(Instant.now().minusSeconds(2 * 60 * 60)), "building-1");
        centralBean(LocalServerHealthMonitorService.class).monitor();

        assertThat(buildingActive("building-1"))
                .as("building-1 must be deactivated once its last_seen_at is stale")
                .isFalse();

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}
