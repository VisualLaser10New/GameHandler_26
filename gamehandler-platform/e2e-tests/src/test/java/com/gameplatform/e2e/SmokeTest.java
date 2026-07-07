package com.gameplatform.e2e;

import com.gameplatform.e2e.harness.DualContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dual-context smoke test — verifies that the central and local Spring contexts
 * can boot simultaneously against H2, that both H2 databases are queryable, and
 * that the {@code users} table exists in both schemas.
 */
@DisplayName("Dual-context smoke test — central + local boot, H2 queryable")
class SmokeTest extends DualContextTestBase {

    @Test
    @DisplayName("Both contexts boot and their H2 databases are queryable")
    void bothContextsBootAndDatabasesAreQueryable() {
        assertThat(centralJdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .as("central H2 responds to SELECT 1")
                .isEqualTo(1);

        assertThat(localJdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .as("local H2 responds to SELECT 1")
                .isEqualTo(1);

        assertThat(centralJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .as("central users table exists and is empty after wipe")
                .isZero();

        assertThat(localJdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class))
                .as("local users table exists and is empty after wipe")
                .isZero();

        System.out.println("[e2e] centralPort=" + centralPort + ", localPort=" + localPort);
    }
}
