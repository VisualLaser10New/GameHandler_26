package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.application.service.UserService;
import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("B16: Double-update of replicated user preserves @Version")
class B16DoubleUpdateReplicatedUserPreservesVersionTest extends DualContextTestBase {

    @Test
    @DisplayName("Replicate user -> update roles -> re-replicate -> local version increments, no exception")
    void doubleUpdatePreservesVersion() {
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        UserService userService = centralBean(UserService.class);
        var user = userService.register("dave", "pw", "dave@x.com");

        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        Long version1 = localJdbcTemplate.queryForObject(
                "SELECT version FROM replicated_users WHERE user_id = ?",
                Long.class, user.getId().value());
        assertThat(version1).isNotNull();
        assertThat(version1).isEqualTo(0L);

        String roles1 = localJdbcTemplate.queryForObject(
                "SELECT roles FROM replicated_users WHERE user_id = ?",
                String.class, user.getId().value());
        assertThat(roles1).contains("USER");

        // Update roles on central -> produces USER_UPDATED outbox event.
        // null password is safe: UserService only changes the password when a
        // non-blank value is supplied, so passing null leaves it unchanged.
        userService.updateUser(user.getId(), null, List.of("OPERATOR"));

        // Second replication -> local must UPDATE (not INSERT), version increments.
        centralBean(UserReplicationSchedulerService.class).replicateUsers();

        Integer count = localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM replicated_users WHERE user_id = ?",
                Integer.class, user.getId().value());
        assertThat(count).isEqualTo(1);

        Long version2 = localJdbcTemplate.queryForObject(
                "SELECT version FROM replicated_users WHERE user_id = ?",
                Long.class, user.getId().value());
        assertThat(version2).isNotNull();
        assertThat(version2).isGreaterThan(version1);

        String roles2 = localJdbcTemplate.queryForObject(
                "SELECT roles FROM replicated_users WHERE user_id = ?",
                String.class, user.getId().value());
        assertThat(roles2).contains("OPERATOR");
        assertThat(roles2).doesNotContain("USER");
    }
}