package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.model.GameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A2 — full chain: client MQTT session start/end → local DB → HTTP sync →
 * central aggregated_statistics.
 *
 * <p>The session id broadcast in the MQTT start payload is NOT the one persisted
 * by the local {@code GameSessionService.start()} (it generates its own UUID),
 * so the end payload must carry the real session id read back from the local
 * {@code game_sessions} table.</p>
 */
@DisplayName("A2: client session start/end flows to central aggregated_statistics")
class A2ClientSessionStartEndFlowsToCentralAggregatedStatisticsTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-chess-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess Table 1", "building-1", "AVAILABLE");
    }

    private String sessionIdByGameAndStatus(String gameId, String status) {
        return localJdbcTemplate.query(
                "SELECT id FROM game_sessions WHERE game_id=? AND status=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId, status);
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
    @DisplayName("client start → local IN_PROGRESS → client end → local COMPLETED → sync → central total_sessions=1")
    void sessionStartEndFlowsToCentral() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        TestClientEmulator client = newClient("client-sess");

        // Publish session start (2 participants — chess requires exactly 2).
        client.getSessionPublisher().publishStart(GAME_ID, "sess-1", GameType.CHESS,
                List.of("user-1", "user-2"));

        // Await the local session (the listener creates a new UUID session id).
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionIdByGameAndStatus(GAME_ID, "IN_PROGRESS")).isNotNull());
        String realSessionId = sessionIdByGameAndStatus(GAME_ID, "IN_PROGRESS");

        // Publish end with the REAL session id (the listener uses the payload's
        // sessionId to look up the session).
        client.getSessionPublisher().publishEnd(GAME_ID, realSessionId, "user-1",
                WinCondition.WIN, "{}");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusByGame(GAME_ID)).isEqualTo("COMPLETED"));

        // Sync to central via the real HTTP sync path and assert aggregated_statistics.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralStat("total_sessions", "CHESS")).isEqualTo(1));

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}
