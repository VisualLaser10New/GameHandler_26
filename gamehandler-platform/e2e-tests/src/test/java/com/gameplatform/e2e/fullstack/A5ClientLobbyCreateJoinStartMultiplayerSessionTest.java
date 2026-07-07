package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.FoosballResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A5 — two clients create, join and start a multiplayer lobby session via MQTT,
 * then the session is ended and synced to central.
 */
@DisplayName("A5: client lobby create/join/start multiplayer session")
class A5ClientLobbyCreateJoinStartMultiplayerSessionTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-foosball-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "FOOSBALL", "Foosball Table 1", "building-1", "AVAILABLE");
    }

    private String sessionIdByGameAndStatus(String gameId, String status) {
        return localJdbcTemplate.query(
                "SELECT id FROM game_sessions WHERE game_id=? AND status=? ORDER BY started_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, gameId, status);
    }

    private String sessionStatusById(String sessionId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_sessions WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, sessionId);
    }

    private Integer participantCount(String sessionId) {
        return localJdbcTemplate.query(
                "SELECT COUNT(*) FROM session_participants WHERE session_id=?",
                rs -> rs.next() ? rs.getInt(1) : 0, sessionId);
    }

    private Integer centralStat(String column, String gameType) {
        return centralJdbcTemplate.query(
                "SELECT " + column + " FROM aggregated_statistics WHERE building_id='building-1' AND game_type=?",
                rs -> rs.next() ? rs.getInt(1) : null, gameType);
    }

    @Test
    @DisplayName("alice creates lobby → bob joins → alice starts → IN_PROGRESS → end → sync → central total_sessions=1")
    void lobbyCreateJoinStartThenEnd() throws Exception {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        seedGame();

        TestClientEmulator alice = newClient("alice");
        TestClientEmulator bob = newClient("bob");

        // Alice creates the lobby.
        alice.getSessionPublisher().publishLobbyCreate(GAME_ID, GameType.FOOSBALL, "alice");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionIdByGameAndStatus(GAME_ID, "WAITING")).isNotNull());
        String sessionId = sessionIdByGameAndStatus(GAME_ID, "WAITING");

        // Bob joins the lobby.
        bob.getSessionPublisher().publishLobbyJoin(GAME_ID, sessionId, "bob");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(participantCount(sessionId)).isEqualTo(2));

        // Alice starts the lobby.
        alice.getSessionPublisher().publishLobbyStart(GAME_ID, sessionId);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(sessionId)).isEqualTo("IN_PROGRESS"));

        // End the session directly through the local service.
        localBean(GameSessionService.class).end(new GameSessionId(sessionId),
                new FoosballResult(null, java.util.List.of(), Map.of(), WinCondition.DRAW));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(sessionStatusById(sessionId)).isEqualTo("COMPLETED"));

        // Sync to central via the real HTTP sync path. The local outbox payload
        // is now read back clean thanks to @Convert on the local OutboxEvent
        // entity, so no manual unwrapping helper is needed.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralStat("total_sessions", "FOOSBALL")).isEqualTo(1));

        try {
            alice.disconnect();
            bob.disconnect();
        } catch (Exception ignored) {
        }
    }
}
