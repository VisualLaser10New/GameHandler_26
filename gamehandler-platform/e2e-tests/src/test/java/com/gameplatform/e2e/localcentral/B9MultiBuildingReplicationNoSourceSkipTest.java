package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.dto.UserSyncDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * B9 — With three active buildings (building-1 real local, building-2 and
 * building-3 WireMock), a central user registration is replicated to ALL
 * active buildings. Each building receives one PUT, the real local saves the
 * user, and {@code replication_progress} has 3 rows.
 */
@DisplayName("B9: Multi-building replication pushes to all active buildings (no source skip)")
class B9MultiBuildingReplicationNoSourceSkipTest extends DualContextTestBase {

    private WireMockServer wireMock;

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
            wireMock = null;
        }
    }

    @Test
    @DisplayName("User registered on central is replicated to all 3 active buildings")
    void multiBuildingReplicationNoSourceSkip() {
        // 1. Start WireMock for building-2 and building-3 (same server, different prefixes)
        wireMock = new WireMockServer(0);
        wireMock.start();
        String building2Url = "http://localhost:" + wireMock.port() + "/building-2";
        String building3Url = "http://localhost:" + wireMock.port() + "/building-3";

        wireMock.stubFor(put(urlEqualTo("/building-2/internal/users/sync"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
        wireMock.stubFor(put(urlEqualTo("/building-3/internal/users/sync"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
        wireMock.stubFor(get(urlEqualTo("/building-2/internal/users/count"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("0")));
        wireMock.stubFor(get(urlEqualTo("/building-3/internal/users/count"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("0")));

        // 2. Register building-1 (real local), building-2 (WireMock), building-3 (WireMock)
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);
        registerBuildingAtCentral("building-2", building2Url);
        registerBuildingAtCentral("building-3", building3Url);

        // 3. Register user on central → PENDING outbox event
        centralBean(UserService.class).register("dave", "pw", "d@x.com");

        // 4. Trigger replication
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 5. Assert all 3 buildings received the PUT
        // building-1 (real local) has the user
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='dave'", Integer.class))
                .as("building-1 (real local) has dave")
                .isEqualTo(1);
        // building-2 WireMock received 1 PUT
        assertThat(wireMock.findAll(putRequestedFor(urlEqualTo("/building-2/internal/users/sync"))).size())
                .as("building-2 WireMock received 1 PUT")
                .isEqualTo(1);
        // building-3 WireMock received 1 PUT
        assertThat(wireMock.findAll(putRequestedFor(urlEqualTo("/building-3/internal/users/sync"))).size())
                .as("building-3 WireMock received 1 PUT")
                .isEqualTo(1);

        // 6. Assert central replication_progress has 3 USER_REGISTERED rows (one per active building).
        // Scoping to event_type='USER_REGISTERED' isolates the user path from the
        // LOCAL_SERVER_REGISTRY_UPSERTED progress rows the registerBuildingAtCentral
        // calls also produce.
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress rp "
                        + "JOIN outbox_events oe ON rp.event_id = oe.id "
                        + "WHERE oe.event_type='USER_REGISTERED'",
                Integer.class))
                .as("replication_progress has 3 USER_REGISTERED rows (one per active building)")
                .isEqualTo(3);

        // 7. Assert central outbox event is SENT
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='SENT'", Integer.class))
                .as("central outbox event is SENT after replication to all buildings")
                .isEqualTo(1);
    }
}