package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
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
 * A4 — client MQTT pause and resume flow end-to-end, then local end → sync.
 */
@DisplayName("A4: client pause and resume session flows end-to-end")
class A4ClientPauseAndResumeSessionFlowsEndToEndTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-chess-2";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess Table 2", "building-1", "AVAILABLE");
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
    @DisplayName("pause via MQTT → PAUSED → resume via MQTT → IN_PROGRESS → end → sync → central total_sessions=1")
    void pauseResumeThenEndFlowsToCentral() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        GameSession session = localBean(GameSessionService.class)
                .start(new GameId(GAME_ID), GameType.CHESS,
                        List.of(new UserId("u-1"), new UserId("u-2")), null);
        String sessionId = session.getId().value();

        TestClientEmulator client = newClient("client-pause");

        // Pause via client MQTT.
        client.getSessionPublisher().publishPause(GAME_ID, sessionId, "user-1");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(sessionId)).isEqualTo("PAUSED"));

        // Resume via client MQTT.
        client.getSessionPublisher().publishResume(GAME_ID, sessionId);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(sessionId)).isEqualTo("IN_PROGRESS"));

        // End the session directly through the local service.
        localBean(GameSessionService.class).end(new GameSessionId(sessionId),
                new ChessResult(null, List.of(), "test", "test", WinCondition.DRAW));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(sessionId)).isEqualTo("COMPLETED"));

        // Sync to central via the real HTTP sync path. The local outbox payload
        // is now read back clean thanks to @Convert on the local OutboxEvent
        // entity, so no manual unwrapping helper is needed.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralStat("total_sessions", "CHESS")).isEqualTo(1));

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}
