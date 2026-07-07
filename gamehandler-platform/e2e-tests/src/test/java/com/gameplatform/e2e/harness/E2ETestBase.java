package com.gameplatform.e2e.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.CentralSystemApplication;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Abstract base for ALL e2e integration tests. Boots the <b>central</b> Spring
 * context via {@code @SpringBootTest} with profile {@code e2e-central} and
 * {@code webEnvironment=RANDOM_PORT}, so the central's HTTP server is live on a
 * random port (injectable via {@link #centralPort}).
 *
 * <p>Imports {@link CleanPayloadOutbox} to install the test-only
 * {@code @Primary OutboxEventRepository} shim that unwraps the H2 JSON
 * double-encoding quirk on the central outbox read path.</p>
 *
 * <p>Subclasses that also need the <b>local</b> context should extend
 * {@link DualContextTestBase}; subclasses that also need a <b>client emulator</b>
 * should extend {@link TripleContextTestBase}.</p>
 */
@SpringBootTest(
        classes = CentralSystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-central")
@Import(CleanPayloadOutbox.class)
public abstract class E2ETestBase {

    /** The central system's actual HTTP port (random). */
    @LocalServerPort
    protected int centralPort;

    /** The central Spring application context. */
    @Autowired
    protected ConfigurableApplicationContext centralContext;

    /** Jackson mapper shared by the central context. */
    @Autowired
    protected ObjectMapper objectMapper;

    /** Central registry port — used by {@link #registerBuildingAtCentral}. */
    @Autowired
    protected LocalServerRegistryPort localServerRegistryPort;

    /** JDBC template for querying the central H2 database. */
    protected JdbcTemplate centralJdbcTemplate;

    /** Embedded MQTT broker — started by {@link DualContextTestBase}. */
    protected MoquetteBroker moquette;

    @Autowired
    public void setCentralJdbcTemplate(DataSource dataSource) {
        this.centralJdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Returns the central system's HTTP port.
     *
     * @return the port number
     */
    protected int centralPort() {
        return centralPort;
    }

    /**
     * Deletes all rows from the central H2 tables that tests may populate.
     * Call this in {@code @BeforeEach} to start each test from a known empty state.
     */
    protected void wipeCentralTables() {
        centralJdbcTemplate.execute("DELETE FROM replication_progress");
        centralJdbcTemplate.execute("DELETE FROM processed_events");
        centralJdbcTemplate.execute("DELETE FROM aggregated_statistics");
        centralJdbcTemplate.execute("DELETE FROM outbox_events");
        centralJdbcTemplate.execute("DELETE FROM local_servers");
        centralJdbcTemplate.execute("DELETE FROM users");
    }

    /**
     * Registers a building at the central system through the real
     * {@link LocalServerRegistryPort} (H2-backed). The registration persists into
     * the {@code local_servers} table and fires the M8 {@code afterCommit}
     * catch-up synchronously (a no-op when the outbox is empty).
     *
     * @param buildingId the building id to register
     * @param baseUrl    the local server's base URL (e.g. {@code http://localhost:<port>})
     */
    protected void registerBuildingAtCentral(String buildingId, String baseUrl) {
        localServerRegistryPort.register(
                new RegisteredLocalServer(new BuildingId(buildingId), baseUrl, Instant.now(), true));
    }

    /**
     * Retrieves a bean from the central Spring context.
     *
     * @param type the bean type
     * @param <T>  the bean type
     * @return the central bean
     */
    protected <T> T centralBean(Class<T> type) {
        return centralContext.getBean(type);
    }
}
