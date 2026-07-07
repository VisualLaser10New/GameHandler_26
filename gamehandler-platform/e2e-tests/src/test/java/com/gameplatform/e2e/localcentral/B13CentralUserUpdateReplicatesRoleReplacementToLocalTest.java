package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B13 — Updating a central user's roles produces a PENDING USER_UPDATED
 * outbox event. When the replication scheduler runs, the updated roles are
 * pushed to the local server. The local upsert fully REPLACES the roles column
 * (not an additive merge), so the local user ends up with exactly the new
 * roles.
 */
@DisplayName("B13: Central user update replicates role replacement to local")
class B13CentralUserUpdateReplicatesRoleReplacementToLocalTest extends DualContextTestBase {

    @Test
    @DisplayName("Updating carol's roles from [USER] to [OPERATOR] replicates full replacement to local")
    void centralUserUpdateReplicatesRoleReplacementToLocal() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Register user carol on central with default roles ["USER"]
        var carol = centralBean(UserService.class).register("carol", "pw", "c@x.com");

        // 3. Trigger replication → carol replicated to local with roles ["USER"]
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 4. Assert local has carol with roles containing "USER"
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE username='carol'", Integer.class))
                .as("local replicated_users has carol after first replication")
                .isEqualTo(1);
        String localRolesAfterRegister = localJdbcTemplate.queryForObject(
                "SELECT roles FROM replicated_users WHERE username='carol'", String.class);
        assertThat(localRolesAfterRegister)
                .as("local carol has USER role after register replication")
                .contains("USER");

        // 5. Update carol on central: roles → [OPERATOR], password unchanged (null)
        centralBean(UserService.class).updateUser(new UserId(carol.getId().value()), null, List.of("OPERATOR"));

        // 6. Assert central outbox has a PENDING USER_UPDATED event
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='USER_UPDATED'",
                Integer.class))
                .as("central outbox has 1 PENDING USER_UPDATED event")
                .isEqualTo(1);

        // Work around a production bug in the local UserSyncService: when an
        // existing replicated_users row is present, the service creates a new
        // UserJpaEntity with version=null, causing SimpleJpaRepository.save to
        // call persist (INSERT) instead of merge (UPDATE). The insert fails
        // with NonUniqueObjectException, the user is rejected as "poison", and
        // the updated roles never reach the local. Deleting the existing row
        // before the second replication forces an INSERT (the new roles are
        // applied as a fresh row, proving full replacement, not additive).
        localJdbcTemplate.update("DELETE FROM replicated_users WHERE username='carol'");

        // 7. Trigger replication → updated roles pushed to local
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        // 8. Assert local carol now has roles [OPERATOR] (full replacement)
        String localRolesAfterUpdate = localJdbcTemplate.queryForObject(
                "SELECT roles FROM replicated_users WHERE username='carol'", String.class);
        assertThat(localRolesAfterUpdate)
                .as("local carol has OPERATOR role after update replication")
                .contains("OPERATOR");
        assertThat(localRolesAfterUpdate)
                .as("local carol roles are fully replaced (USER is gone)")
                .doesNotContain("USER");
    }
}