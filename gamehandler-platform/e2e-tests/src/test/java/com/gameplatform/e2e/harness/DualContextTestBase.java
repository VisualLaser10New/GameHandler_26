package com.gameplatform.e2e.harness;

import com.gameplatform.local.LocalServerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;

/**
 * Extends {@link E2ETestBase} and additionally boots the <b>local</b> Spring
 * context in a separate {@link ConfigurableApplicationContext} (via
 * {@link SpringApplicationBuilder}), sharing the embedded Moquette MQTT broker
 * with the local context's {@code MqttConfig} bean.
 *
 * <p>Uses {@link TestInstance(Lifecycle#PER_CLASS)} so that {@code @BeforeAll}
 * can be non-static (it needs the central port injected by Spring Test, which is
 * only available on the test instance).</p>
 *
 * <p><b>Lifecycle:</b></p>
 * <ol>
 *   <li>Central context is booted by {@link E2ETestBase}'s {@code @SpringBootTest}
 *       before any test lifecycle callback.</li>
 *   <li>{@code @BeforeAll} — starts Moquette, finds a free HTTP port, boots the
 *       local context with {@code mqtt.broker-url} and {@code app.central-system-url}
 *       pointing to the live central server.</li>
 *   <li>{@code @BeforeEach} — wipes all central and local H2 tables.</li>
 *   <li>{@code @AfterAll} — closes the local context and stops Moquette.</li>
 * </ol>
 *
 * <p><b>Auto-registration caveat:</b> the local's
 * {@code LocalServerRegistrationService} (SmartLifecycle) auto-registers
 * {@code building-1} with the central on startup. The {@code @BeforeEach} wipe
 * removes the {@code local_servers} row, so tests that need the local registered
 * must call {@link #registerBuildingAtCentral} explicitly in the test body.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DualContextTestBase extends E2ETestBase {

    /** The local Spring application context (separate from the central one). */
    protected ConfigurableApplicationContext localContext;

    /** The local server's actual HTTP port. */
    protected int localPort;

    /** JDBC template for querying the local H2 database. */
    protected JdbcTemplate localJdbcTemplate;

    /**
     * Starts the Moquette broker and boots the local Spring context.
     * Runs once per test class (non-static, after the central context is up).
     *
     * @throws Exception if the broker or the local context fails to start
     */
    @BeforeAll
    void startLocalAndBroker() throws Exception {
        moquette = new MoquetteBroker();
        moquette.start();

        int localHttpPort = findFreePort();

        // Pass runtime-injected properties as command-line args so they have the
        // HIGHEST precedence — overriding the placeholders in application-e2e-local.yml.
        // (SpringApplicationBuilder.properties() sets DEFAULTS with the lowest precedence,
        //  which the profile yml would override.)
        localContext = new SpringApplicationBuilder(LocalServerApplication.class)
                .profiles("e2e-local")
                .run(
                        "--server.port=" + localHttpPort,
                        "--app.central-system-url=http://localhost:" + centralPort,
                        "--app.local-base-url=http://localhost:" + localHttpPort,
                        "--mqtt.broker-url=tcp://localhost:" + moquette.getPort()
                );

        if (localContext instanceof WebServerApplicationContext wsac) {
            this.localPort = wsac.getWebServer().getPort();
        } else {
            this.localPort = localHttpPort;
        }

        DataSource localDataSource = localContext.getBean(DataSource.class);
        this.localJdbcTemplate = new JdbcTemplate(localDataSource);
    }

    /**
     * Closes the local context and stops the Moquette broker.
     */
    @AfterAll
    void stopLocalAndBroker() {
        if (localContext != null) {
            localContext.close();
            localContext = null;
        }
        if (moquette != null) {
            moquette.stop();
            moquette = null;
        }
    }

    /**
     * Wipes all central and local H2 tables before each test so every
     * {@code @Test} starts from a known empty state.
     */
    @BeforeEach
    void wipeAllTables() {
        wipeCentralTables();
        wipeLocalTables();
    }

    /**
     * Deletes all rows from the local H2 tables. Child tables are deleted first
     * to satisfy FK constraints (session_participants → game_sessions).
     * Tables that do not exist yet are silently skipped.
     */
    protected void wipeLocalTables() {
        if (localJdbcTemplate == null) {
            return;
        }
        safeDelete(localJdbcTemplate, "session_participants");
        safeDelete(localJdbcTemplate, "game_sessions");
        safeDelete(localJdbcTemplate, "reservations");
        safeDelete(localJdbcTemplate, "outbox_events");
        safeDelete(localJdbcTemplate, "outbox_dead_letter");
        safeDelete(localJdbcTemplate, "game_catalog");
        safeDelete(localJdbcTemplate, "users");
        safeDelete(localJdbcTemplate, "replicated_users");
    }

    /**
     * Retrieves a bean from the local Spring context.
     *
     * @param type the bean type
     * @param <T>  the bean type
     * @return the local bean
     */
    protected <T> T localBean(Class<T> type) {
        return localContext.getBean(type);
    }

    /**
     * Finds a free TCP port on localhost by opening and immediately closing a
     * {@link ServerSocket}(0). There is a small TOCTOU window before the caller
     * binds the port, but this is acceptable for test harness use.
     *
     * @return a free port number
     * @throws IOException if the socket cannot be opened
     */
    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void safeDelete(JdbcTemplate jdbc, String table) {
        try {
            jdbc.execute("DELETE FROM " + table);
        } catch (DataAccessException e) {
            // table may not exist yet in this test's schema; silently skip
        }
    }
}
