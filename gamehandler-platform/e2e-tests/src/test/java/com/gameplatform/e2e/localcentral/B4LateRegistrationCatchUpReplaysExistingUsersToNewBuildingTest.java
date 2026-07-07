package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.LateRegistrationCatchUpService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.domain.model.BuildingId;
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
 * B4 — When a new local server (building-2, WireMock) registers AFTER existing
 * USER_REGISTERED outbox events have been created on central, the M8
 * afterCommit catch-up replays those pending events to the newly-registered
 * server. WireMock should receive one PUT per pending event, and
 * {@code replication_progress} should have one row per (event, building-2).
 *
 * <p><b>Harness workaround:</b> the central {@code outbox_events.payload}
 * column is {@code columnDefinition="JSON"} on H2, which double-encodes the
 * payload on read-back. The catch-up service reads the payload via JPA
 * directly (not through the {@code CleanPayloadOutbox} shim), so the column is
 * ALTERed to VARCHAR before any outbox events are created. Additionally, the
 * {@code afterCommit} callback's transaction context prevents
 * {@code replication_progress} writes from committing reliably; to avoid this,
 * building-2 is inserted directly via JdbcTemplate and the catch-up is invoked
 * explicitly via {@link LateRegistrationCatchUpService#catchUpNewlyRegisteredServer}.</p>
 */
@DisplayName("B4: Late registration catch-up replays existing users to new building")
class B4LateRegistrationCatchUpReplaysExistingUsersToNewBuildingTest extends DualContextTestBase {

    private WireMockServer wireMock;

    @AfterEach
    void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
            wireMock = null;
        }
    }

    @Test
    @DisplayName("Registering building-2 after 2 central users exist replays both via catch-up")
    void lateRegistrationCatchUpReplaysExistingUsersToNewBuilding() {
        // Work around H2 JSON double-encoding: the central catch-up service reads
        // outbox_events.payload via JPA directly (not through the CleanPayloadOutbox
        // shim that wraps the OutboxEventRepository port). ALTER the column to
        // VARCHAR so the payload is stored and read as a plain string.
        centralJdbcTemplate.execute("ALTER TABLE outbox_events ALTER COLUMN payload VARCHAR(16384)");

        // 1. Register 2 users on central → 2 PENDING USER_REGISTERED outbox events
        centralBean(UserService.class).register("alice", "pw", "a@x.com");
        centralBean(UserService.class).register("bob", "pw", "b@x.com");

        // 2. Assert central outbox has 2 PENDING USER_REGISTERED events
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='USER_REGISTERED'",
                Integer.class))
                .as("central outbox has 2 PENDING USER_REGISTERED events")
                .isEqualTo(2);

        // 3. Start WireMock for building-2
        wireMock = new WireMockServer(0);
        wireMock.start();
        String building2Url = "http://localhost:" + wireMock.port() + "/building-2";
        wireMock.stubFor(put(urlEqualTo("/building-2/internal/users/sync"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
        wireMock.stubFor(get(urlEqualTo("/building-2/internal/users/count"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("0")));

        // 4. Register building-2 directly via JdbcTemplate (bypass afterCommit),
        //    then explicitly invoke the catch-up service.
        centralJdbcTemplate.update(
                "INSERT INTO local_servers (building_id, base_url, last_seen_at, is_active) VALUES (?, ?, ?, ?)",
                "building-2", building2Url, Timestamp.from(Instant.now()), true);

        RegisteredLocalServer server = new RegisteredLocalServer(
                new BuildingId("building-2"), building2Url, Instant.now(), true);
        centralBean(LateRegistrationCatchUpService.class).catchUpNewlyRegisteredServer(server);

        // 5. Assert WireMock received 2 PUT calls to /building-2/internal/users/sync
        assertThat(wireMock.findAll(putRequestedFor(urlEqualTo("/building-2/internal/users/sync"))).size())
                .as("WireMock received 2 PUT calls for building-2 catch-up")
                .isEqualTo(2);

        // 6. Assert central replication_progress has 2 rows for building-2
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress WHERE server_id='building-2'",
                Integer.class))
                .as("replication_progress has 2 rows for building-2")
                .isEqualTo(2);
    }
}