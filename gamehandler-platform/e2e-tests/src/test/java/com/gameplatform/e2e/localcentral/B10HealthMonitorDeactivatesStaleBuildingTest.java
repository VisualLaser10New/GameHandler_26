package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.LocalServerHealthMonitorService;
import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * B10 — When building-1's {@code last_seen_at} is stale (30 min ago), the
 * health monitor deactivates it. Subsequent replication only pushes to the
 * still-active building-2 (WireMock); building-1 is skipped.
 */
@DisplayName("B10: Health monitor deactivates stale building and stops replication to it")
class B10HealthMonitorDeactivatesStaleBuildingTest extends DualContextTestBase {

    private WireMockServer wireMock;

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
            wireMock = null;
        }
    }

    @Test
    @DisplayName("Stale building-1 is deactivated; only building-2 receives replication")
    void healthMonitorDeactivatesStaleBuildingStopsReplication() {
        // 1. Start WireMock for building-2
        wireMock = new WireMockServer(0);
        wireMock.start();
        String building2Url = "http://localhost:" + wireMock.port() + "/building-2";
        wireMock.stubFor(put(urlEqualTo("/building-2/internal/users/sync"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
        wireMock.stubFor(get(urlEqualTo("/building-2/internal/users/count"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("0")));

        // 2. Register building-1 (real local) and building-2 (WireMock)
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        registerBuildingAtCentral("building-2", building2Url);

        // 3. Set building-1's last_seen_at to now - 30 min
        centralJdbcTemplate.update(
                "UPDATE local_servers SET last_seen_at = ? WHERE building_id = 'building-1'",
                Timestamp.from(Instant.now().minusSeconds(1800)));

        // 4. Trigger health monitor — deactivates stale building-1
        centralBean(LocalServerHealthMonitorService.class).monitor();

        // 5. Assert building-1 is now inactive
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT is_active FROM local_servers WHERE building_id='building-1'", Integer.class))
                .as("building-1 is deactivated (is_active=0)")
                .isEqualTo(0);

        // 6. Register user on central → PENDING outbox
        centralBean(UserService.class).register("eve", "pw", "e@x.com");

        // 7. Trigger replication — only building-2 is active
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 8. Assert only building-2 (WireMock) received the PUT
        assertThat(wireMock.findAll(putRequestedFor(urlEqualTo("/building-2/internal/users/sync"))).size())
                .as("building-2 WireMock received 1 PUT")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='eve'", Integer.class))
                .as("building-1 (deactivated) did not receive the replication — 0 eve rows")
                .isEqualTo(0);

        // 9. Assert central replication_progress has 1 row (for building-2 only)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE server_id='building-2'",
                Integer.class))
                .as("replication_progress has 1 row for building-2 only")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE server_id='building-1'",
                Integer.class))
                .as("replication_progress has 0 rows for building-1 (skipped)")
                .isEqualTo(0);
    }
}