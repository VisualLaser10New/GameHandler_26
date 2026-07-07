package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.AlertPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A7 — when a client stops sending heartbeats, the local server publishes an
 * UNREACHABLE alert to MQTT, aborts the active session, and syncs the
 * GAME_SESSION_ABORTED outbox event to central (total_aborted_sessions=1,
 * total_sessions=0).
 *
 * <p>The game machine is seeded with {@code building_id='building-2'} so the
 * server's own heartbeat PINGs (published on the <em>game's</em> building id)
 * do not echo back into its {@code building-1} subscription and reset the
 * missed counter. The client still publishes its heartbeats on
 * {@code building-1} (its own building), which the local server receives and
 * registers. Alerts are always published on the <em>server's</em> building id
 * ({@code building-1/alerts}), so the client subscribes there.</p>
 */
@DisplayName("A7: client heartbeat missed triggers alert and aborts session")
class A7ClientHeartbeatMissedTriggersAlertAndAbortsSessionTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-alert-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess Alert 1", "building-2", "AVAILABLE");
    }

    private String sessionStatusByGame(String gameId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE game_id=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId);
    }

    private Integer centralStat(String column, String gameType) {
        return centralJdbcTemplate.query(
                "SELECT " + column + " FROM aggregated_statistics WHERE building_id='building-1' AND game_type=?",
                rs -> rs.next() ? rs.getInt(1) : null, gameType);
    }

    @Test
    @DisplayName("missed heartbeats → UNREACHABLE alert + local ABORTED → sync → central total_aborted=1, total_sessions=0")
    void heartbeatMissedTriggersAlertAndAbortsSession() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        // Start an active session directly through the local service (CHESS
        // requires exactly 2 participants).
        localBean(GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.CHESS,
                        List.of(new UserId("user-1"), new UserId("user-2")), null);

        TestClientEmulator client = newClient("client-alert");

        // Subscribe to the alerts topic BEFORE triggering the abort so the
        // alert published by HealthCheckService is captured.
        CountDownLatch alertLatch = new CountDownLatch(1);
        AtomicReference<AlertPayload> receivedAlert = new AtomicReference<>();
        client.getAdapter().subscribe("building/building-1/alerts", 1, (topic, message) -> {
            try {
                AlertPayload alert = MqttPayloadSerializer.deserialize(message.getPayload(), AlertPayload.class);
                receivedAlert.set(alert);
            } catch (Exception ignored) {
                // leave receivedAlert null; the latch assertion will fail
            }
            alertLatch.countDown();
        });

        // Send a couple of heartbeats so the first health-check cycle sees a
        // responded client and does NOT abort (it merely resets the cycle).
        for (int i = 0; i < 2; i++) {
            client.getHeartbeatPublisher().publishHeartbeat(GAME_ID);
            Thread.sleep(80);
        }
        Thread.sleep(300);

        // Cycle #1: responded=true → missed reset to 0, flag cleared. Session
        // must remain IN_PROGRESS.
        localBean(HealthCheckService.class).performHealthCheck();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("IN_PROGRESS"));

        // Now STOP heartbeats and run health checks until the missed counter
        // reaches 3 (3 further cycles: missed=1, 2, 3→abort+alert).
        Thread.sleep(300);
        await().atMost(20, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            for (int i = 0; i < 3; i++) {
                localBean(HealthCheckService.class).performHealthCheck();
            }
            assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("ABORTED");
        });

        // The client must have received the UNREACHABLE alert on the alerts topic.
        assertThat(alertLatch.await(5, TimeUnit.SECONDS))
                .as("client should receive the UNREACHABLE alert on building/building-1/alerts")
                .isTrue();
        assertThat(receivedAlert.get()).isNotNull();
        assertThat(receivedAlert.get().alertType()).isEqualTo("UNREACHABLE");
        assertThat(receivedAlert.get().gameId()).isEqualTo(GAME_ID);

        // Sync to central via the real HTTP sync path. The local outbox payload
        // is now read back clean thanks to @Convert on the local OutboxEvent
        // entity, so no manual unwrapping helper is needed.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(centralStat("total_aborted_sessions", "CHESS")).isEqualTo(1);
            assertThat(centralStat("total_sessions", "CHESS")).isEqualTo(0);
        });

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}