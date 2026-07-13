package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B5 — After a user is replicated to building-1 (real local), deactivating
 * building-1 and re-registering it fires the M8 catch-up. The catch-up's
 * {@code existsByEventIdAndServerId} check skips events already recorded in
 * {@code replication_progress}, so no duplicate user row is created on the
 * local side and no extra progress row is written.
 */
@DisplayName("B5: Late registration catch-up does not re-duplicate already replicated users")
class B5LateRegistrationDoesNotReduplicateTest extends DualContextTestBase {

    @Test
    @DisplayName("Re-registering building-1 after replication does not duplicate the local user")
    void catchUpDoesNotReduplicateAlreadyReplicatedUser() {
        // 1. Register user on central
        centralBean(UserService.class).register("carol", "pw", "c@x.com");

        // 2. Register building-1 at central (real local)
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 3. Trigger replication — user replicated to building-1
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 4. Assert building-1 (real local) has the user
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='carol'", Integer.class))
                .as("local replicated_users has carol after first replication")
                .isEqualTo(1);
        // Scope progress to USER_REGISTERED so the LOCAL_SERVER_REGISTRY_UPSERTED
        // progress row from registerBuildingAtCentral does not inflate the count.
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress rp "
                        + "JOIN outbox_events oe ON rp.event_id = oe.id "
                        + "WHERE rp.server_id='building-1' AND oe.event_type='USER_REGISTERED'",
                Integer.class))
                .as("replication_progress has 1 USER_REGISTERED row for building-1 after first replication")
                .isEqualTo(1);

        // 5. Deactivate building-1 so the re-registration triggers catch-up (wasInactive=true)
        centralJdbcTemplate.update(
                "UPDATE local_servers SET is_active = 0 WHERE building_id = 'building-1'");

        // 6. Re-register building-1 — wasInactive=true → catch-up fires
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 7. Assert local still has exactly 1 copy of the user (no duplicate)
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='carol'", Integer.class))
                .as("local still has exactly 1 carol after re-registration catch-up")
                .isEqualTo(1);

        // 8. Assert central replication_progress still has exactly 1 USER_REGISTERED row for building-1
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replication_progress rp "
                        + "JOIN outbox_events oe ON rp.event_id = oe.id "
                        + "WHERE rp.server_id='building-1' AND oe.event_type='USER_REGISTERED'",
                Integer.class))
                .as("replication_progress still has exactly 1 USER_REGISTERED row for building-1")
                .isEqualTo(1);
    }
}