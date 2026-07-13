package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2 — A user registered on the central system produces a PENDING
 * USER_REGISTERED outbox event. When the replication scheduler runs, the event
 * is pushed to the registered local server (real HTTP PUT), a
 * {@code replication_progress} row is recorded for building-1, the outbox event
 * transitions to SENT, and the local {@code replicated_users} table holds the
 * replicated user.
 */
@DisplayName("B2: Central registered user replicated to local")
class B2CentralRegisteredUserReplicatedToLocalTest extends DualContextTestBase {

    @Test
    @DisplayName("Central user registered is replicated to building-1 local via replicateUsers()")
    void centralUserReplicatedToLocal() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Register user on central
        centralBean(UserService.class).register("bob", "pw", "b@x.com");

        // 3. Assert central outbox has 1 PENDING USER_REGISTERED row
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='USER_REGISTERED'",
                Integer.class))
                .as("central outbox has 1 PENDING USER_REGISTERED event")
                .isEqualTo(1);

        // 4. Trigger replication — pushes to the real local via HTTP PUT
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 5. Assert central replication_progress has 1 USER_REGISTERED row for building-1.
        // (Scoping to event_type='USER_REGISTERED' isolates the user path from the
        // LOCAL_SERVER_REGISTRY_UPSERTED progress row that registerBuildingAtCentral
        // also produces — see LocalServerRepositoryAdapter.register outbox emit.)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress rp "
                        + "JOIN outbox_events oe ON rp.event_id = oe.id "
                        + "WHERE rp.server_id='building-1' AND oe.event_type='USER_REGISTERED'",
                Integer.class))
                .as("replication_progress has 1 USER_REGISTERED row for building-1")
                .isEqualTo(1);

        // 6. Assert central outbox event is SENT. Scope to USER_REGISTERED so the
        // LOCAL_SERVER_REGISTRY_UPSERTED event (also drained by replicateUsers)
        // does not inflate the count.
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='SENT' AND event_type='USER_REGISTERED'",
                Integer.class))
                .as("central outbox event is SENT after replication")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'", Integer.class))
                .as("no PENDING outbox events remain after replication")
                .isEqualTo(0);

        // 7. Assert local replicated_users has bob
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='bob'", Integer.class))
                .as("local replicated_users has bob after replication")
                .isEqualTo(1);
    }
}