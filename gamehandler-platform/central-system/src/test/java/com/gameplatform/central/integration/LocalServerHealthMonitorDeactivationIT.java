package com.gameplatform.central.integration;

import com.gameplatform.central.application.service.LocalServerHealthMonitorService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalServerJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 — H2 integration test for {@link LocalServerHealthMonitorService}.
 *
 * <p>Seeds three {@code local_servers} rows and invokes {@code monitor()}
 * directly (NOT relying on the scheduler, which is disabled via
 * {@code app.health.monitor-interval-ms: 999999999} in {@code application-test.yml}):
 * <ul>
 *   <li>{@code building-active-recent}  — {@code is_active=true},
 *       {@code last_seen_at = now} → must remain active.</li>
 *   <li>{@code building-active-stale}   — {@code is_active=true},
 *       {@code last_seen_at = now − 30 min} → must flip to inactive.</li>
 *   <li>{@code building-already-off}    — {@code is_active=false},
 *       {@code last_seen_at = now − 30 min} → must remain inactive.</li>
 * </ul>
 * The default {@code app.health.server-stale-threshold-ms} (= 900000 ms = 15 min)
 * is used, so the 30-min-old rows are above the threshold while the recent row
 * is below it.</p>
 *
 * <p><b>H2 vs MySQL note:</b> H2 (MODE=MySQL) preserves {@code TIMESTAMP(9)}
 * precision by default; {@code last_seen_at} is mapped as {@code Instant} →
 * {@code TIMESTAMP} which on H2 keeps enough nanosecond precision for the
 * {@code NOW_MILLIS()}−30 min offset used here. The 30-min gap is far larger
 * than any sub-millisecond rounding error, so the comparisons are stable.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("M13: LocalServerHealthMonitorService deactivation (H2)")
class LocalServerHealthMonitorDeactivationIT {

    @Autowired
    private LocalServerHealthMonitorService monitorService;

    @Autowired
    private LocalServerRegistryPort localServerRegistryPort;

    @Autowired
    private LocalServerJpaRepository localServerJpaRepository;

    private JdbcTemplate jdbcTemplate;

    @Autowired
    public void setJdbcTemplate(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        // Central ITs share the H2 in-memory DB across the SpringBoot context;
        // clean our rows so other tests in the same class start fresh.
        jdbcTemplate.execute("DELETE FROM local_servers");
    }

    private void seed(String buildingId, String baseUrl, Instant lastSeenAt, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO local_servers (building_id, base_url, last_seen_at, is_active) VALUES (?, ?, ?, ?)",
                buildingId, baseUrl, lastSeenAt, active);
    }

    @Test
    @DisplayName("monitor() flips is_active=false only for the active-stale server")
    void monitorDeactivatesOnlyTheActiveStaleServer() {
        Instant now = Instant.now();
        Instant thirtyMinutesAgo = now.minusSeconds(30 * 60);

        seed("building-active-recent", "http://recent:8081", now, true);
        seed("building-active-stale", "http://stale:8081", thirtyMinutesAgo, true);
        seed("building-already-off", "http://off:8081", thirtyMinutesAgo, false);

        monitorService.monitor();

        List<RegisteredLocalServer> after = localServerRegistryPort.findAll();
        assertThat(after).hasSize(3);

        RegisteredLocalServer recent = after.stream()
                .filter(s -> s.getBuildingId().equals(new BuildingId("building-active-recent")))
                .findFirst().orElseThrow();
        assertThat(recent.isActive())
                .as("active-recent server must remain active")
                .isTrue();

        RegisteredLocalServer stale = after.stream()
                .filter(s -> s.getBuildingId().equals(new BuildingId("building-active-stale")))
                .findFirst().orElseThrow();
        assertThat(stale.isActive())
                .as("active-stale server must have been deactivated")
                .isFalse();

        RegisteredLocalServer alreadyOff = after.stream()
                .filter(s -> s.getBuildingId().equals(new BuildingId("building-already-off")))
                .findFirst().orElseThrow();
        assertThat(alreadyOff.isActive())
                .as("already-inactive server must remain inactive")
                .isFalse();
    }

    @Test
    @DisplayName("deactivate() called twice on the same building is idempotent")
    void deactivateIsIdempotent() {
        Instant now = Instant.now();
        seed("building-idempotent", "http://idem:8081", now.minusSeconds(60 * 60), true);

        // First invocation flips is_active to false.
        localServerRegistryPort.deactivate(new BuildingId("building-idempotent"));
        localServerRegistryPort.deactivate(new BuildingId("building-idempotent"));

        RegisteredLocalServer server = localServerRegistryPort.findAll().stream()
                .filter(s -> s.getBuildingId().equals(new BuildingId("building-idempotent")))
                .findFirst().orElseThrow();
        assertThat(server.isActive()).isFalse();
        // Row is preserved (deactivate must NOT delete the registration).
        assertThat(localServerJpaRepository.findById("building-idempotent"))
                .as("deactivate must not delete the local_servers row")
                .isPresent();
    }
}