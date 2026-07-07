package com.gameplatform.e2e.fullstack;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A6 — a client game-state change updates the local game machine status only;
 * no central aggregated_statistics row is expected (game state is local-only).
 */
@DisplayName("A6: client game state change updates local game machine status")
class A6ClientGameStateChangeUpdatesLocalGameMachineStatusTest extends TripleContextTestBase {

    private static final String GAME_ID = "game-state-1";

    private void seedGame() {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status) VALUES (?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Chess State 1", "building-1", "AVAILABLE");
    }

    private String localGameStatus(String gameId) {
        return localJdbcTemplate.query(
                "SELECT status FROM game_catalog WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, gameId);
    }

    private Integer centralAggregatedCount() {
        return centralJdbcTemplate.query(
                "SELECT COUNT(*) FROM aggregated_statistics",
                rs -> rs.next() ? rs.getInt(1) : 0);
    }

    @Test
    @DisplayName("client publishes IN_USE state → local game_catalog IN_USE; central aggregated_statistics stays empty")
    void clientGameStateChangeUpdatesLocalOnly() throws Exception {
        seedGame();

        TestClientEmulator client = newClient("client-state");

        client.getGameStatePublisher().publishState(GAME_ID, GameMachineStatus.IN_USE);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(localGameStatus(GAME_ID)).isEqualTo("IN_USE"));

        // Game-state changes do not create outbox events, so syncing (even if
        // invoked) must not produce any central aggregated_statistics row.
        localBean(SyncSchedulerService.class).syncWithCentral();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(centralAggregatedCount()).isZero());

        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }
}
