package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * B14 — {@code GAME_SESSION_COMPLETED} events for distinct (building, gameType)
 * pairs produce distinct {@code aggregated_statistics} rows. Events for
 * building-1/CHESS, building-1/FOOSBALL, and building-2/CHESS yield exactly 3
 * rows each with total_sessions=1, and no (building-2, FOOSBALL) row exists.
 */
@DisplayName("B14: Stats aggregation distinct per (building, gameType, period)")
class B14StatsAggregationDistinctPerBuildingGamePeriodTest extends DualContextTestBase {

    private WireMockServer wireMock;

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
            wireMock = null;
        }
    }

    @Test
    @DisplayName("3 events for 3 distinct (building,gameType) pairs → 3 rows, each total_sessions=1")
    void statsAggregationDistinctPerBuildingGamePeriod() {
        // 1. Register building-1 and building-2 (WireMock, just for registration) at central
        wireMock = new WireMockServer(0);
        wireMock.start();
        String building2Url = "http://localhost:" + wireMock.port() + "/building-2";
        wireMock.stubFor(put(urlEqualTo("/building-2/internal/users/sync"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
        wireMock.stubFor(get(urlEqualTo("/building-2/internal/users/count"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("0")));

        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        registerBuildingAtCentral("building-2", building2Url);

        // 2. Send GAME_SESSION_COMPLETED for building-1/CHESS
        sendSessionCompleted("building-1", "CHESS");
        // 3. Send GAME_SESSION_COMPLETED for building-1/FOOSBALL
        sendSessionCompleted("building-1", "FOOSBALL");
        // 4. Send GAME_SESSION_COMPLETED for building-2/CHESS
        sendSessionCompleted("building-2", "CHESS");

        // 5. Assert central aggregated_statistics has exactly 3 rows
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aggregated_statistics", Integer.class))
                .as("aggregated_statistics has exactly 3 rows")
                .isEqualTo(3);

        // 6. Assert each has total_sessions=1
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='CHESS'",
                Integer.class))
                .as("building-1/CHESS total_sessions=1")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='FOOSBALL'",
                Integer.class))
                .as("building-1/FOOSBALL total_sessions=1")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-2' AND game_type='CHESS'",
                Integer.class))
                .as("building-2/CHESS total_sessions=1")
                .isEqualTo(1);

        // 7. Assert NO (building-2, FOOSBALL) row exists
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aggregated_statistics WHERE building_id='building-2' AND game_type='FOOSBALL'",
                Integer.class))
                .as("no (building-2, FOOSBALL) row exists")
                .isEqualTo(0);
    }

    private void sendSessionCompleted(String buildingId, String gameType) {
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-07-05T12:00:00Z";
        String payload = "{\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"sessionId\":\"sess-" + gameType.toLowerCase() + "\","
                + "\"gameType\":\"" + gameType + "\","
                + "\"durationSeconds\":120,"
                + "\"status\":\"COMPLETED\","
                + "\"resultJson\":null}";
        OutboxEventDto event = new OutboxEventDto(
                eventId, "GAME_SESSION_COMPLETED", payload, Instant.parse(occurredAt));
        SyncPayloadDto batch = new SyncPayloadDto(buildingId, List.of(event));
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);
    }
}