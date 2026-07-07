package com.gameplatform.e2e.localcentral;

import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B12 — Registering a building at central through the real
 * {@code LocalServerRegistryPort} persists exactly one {@code local_servers}
 * row. Re-registering the same building is idempotent: the existing row is
 * updated (not duplicated), so exactly one row remains.
 */
@DisplayName("B12: Local server auto-registration on startup is idempotent")
class B12LocalServerAutoRegistrationOnStartupTest extends DualContextTestBase {

    @Test
    @DisplayName("Register building-1 once → 1 row; re-register → still 1 row (idempotent)")
    void localServerRegistrationIsIdempotent() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Assert central local_servers has building-1 with is_active=1
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM local_servers WHERE building_id='building-1'",
                Integer.class))
                .as("local_servers has 1 row for building-1 after first registration")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT is_active FROM local_servers WHERE building_id='building-1'",
                Integer.class))
                .as("building-1 is active")
                .isEqualTo(1);

        // 3. Register again (idempotent — simulating restart)
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 4. Assert central local_servers still has exactly 1 row for building-1
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM local_servers WHERE building_id='building-1'",
                Integer.class))
                .as("local_servers still has exactly 1 row for building-1 (no duplicate)")
                .isEqualTo(1);
    }
}