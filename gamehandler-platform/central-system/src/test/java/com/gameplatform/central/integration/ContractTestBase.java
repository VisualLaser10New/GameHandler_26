package com.gameplatform.central.integration;

import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

/**
 * Base class for integration tests of the central-system wire contracts.
 * Boots the full central Spring context against an H2 in-memory database
 * (profile "test"). Subclasses are expected to use WireMock to assert on
 * outbound REST traffic and JdbcTemplate to assert on H2 table state.
 *
 * <p>FASE 5: scope intentionally reduced from the plan's dual-context E2E
 * (central+local in one Spring context) to a single central context — the
 * local-server side is exercised via WireMock HTTP stubs. The full
 * EndToEndSimulationIT (plan lines 189-198) is left as future work.</p>
 *
 * <p>Special test-time wiring:
 * <ul>
 *   <li>{@code @MockBean LocalServerRegistryPort} — stubs the registry so the
 *       {@code UserReplicationSchedulerService.replicateUsers()} periodic task
 *       (and the {@code LateRegistrationCatchUpService}) does not attempt real
 *       outbound REST calls to non-existent local servers during the test.</li>
 *   <li>{@code @ActiveProfiles("test")} — activates the H2 + disable-SSL
 *       application-test.yml.</li>
 *   <li>Schedulers are still enabled (real {@code @EnableScheduling}) but the
 *       intervals in application-test.yml are 999999999 ms so they fire at most
 *       once during a short test run, and the mock registry returns an empty
 *       active-server list anyway, making the periodic task a no-op.</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ContractTestBase {

    protected WireMockServer wireMockServer;
    protected JdbcTemplate jdbcTemplate;

    @MockBean
    protected LocalServerRegistryPort localServerRegistryPort;

    @BeforeEach
    void baseSetUp() {
        wireMockServer = new WireMockServer(0); // random free port
        wireMockServer.start();
        // central-system-url is configured to https://central-test:8080 by application-test.yml.
        // Tests that need REST outbound will override the adapter's centralSystemUrl programmatically
        // OR reconfigure the URL via @DynamicPropertySource. For this scoped-down test, we'll mostly
        // test inbound contracts (local → central) which don't need central outbound.
        if (jdbcTemplate != null) {
            // Clean tables between tests so each parametrized invocation starts fresh.
            jdbcTemplate.execute("DELETE FROM processed_events");
            jdbcTemplate.execute("DELETE FROM aggregated_statistics");
            jdbcTemplate.execute("DELETE FROM local_servers");
            jdbcTemplate.execute("DELETE FROM outbox_events");
            jdbcTemplate.execute("DELETE FROM users");
        }
    }

    @AfterEach
    void baseTearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired
    public void setJdbcTemplate(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
}
