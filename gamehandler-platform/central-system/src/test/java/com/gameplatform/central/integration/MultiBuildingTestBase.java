package com.gameplatform.central.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.application.service.UserReplicationSchedulerService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ReplicationProgressJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Base class for multi-building end-to-end integration tests.
 *
 * <p>Boots the full central Spring context against H2 (profile {@code test},
 * MODE=MySQL) exactly like {@link ContractTestBase}, but <b>deliberately does
 * NOT</b> replace {@link LocalServerRegistryPort} with a {@code @MockBean}.</p>
 *
 * <p>The whole point of this base is to wire the REAL H2-backed
 * {@code LocalServerRepositoryAdapter} bean so that
 * {@code register(building, baseUrl)} calls persist into the {@code local_servers}
 * table, the M8 {@code afterCommit} catch-up actually runs, and
 * {@link UserReplicationSchedulerService#replicateUsers()} sees the registered
 * servers when it queries {@link LocalServerRegistryPort#getActiveLocalServers()}.
 *
 * <p><b>WireMock setup:</b> a SINGLE {@link WireMockServer} is started on a random
 * port per test. Each building's {@code baseUrl} is a distinct URL PREFIX on that
 * same server ({@code http://localhost:<port>/building-1},
 * {@code .../building-2}, {@code .../building-3}). The
 * {@code LocalServerRestAdapter} issues {@code PUT <baseUrl>/internal/users/sync},
 * so three stubs are registered — one per building prefix — each returning the M3
 * ack contract {@code [{"userId":"any","applied":true,"reason":null}]}. The
 * central scheduler records {@code replication_progress} for any ack with
 * {@code applied=true} regardless of the ack's {@code userId} (the userId is not
 * matched against the pushed user), so a single fixed ack body suffices. A
 * defensive {@code GET <baseUrl>/internal/users/count} stub returning {@code 0}
 * is also registered (used only if a scenario invokes the count port).</p>
 *
 * <p><b>Cleanup:</b> tables are wiped in {@link #baseSetUp()} via
 * {@code JdbcTemplate} — matching {@link ContractTestBase}'s convention (no
 * {@code @Transactional}/{@code @Rollback} on the base; the real adapter's own
 * {@code @Transactional} proxies commit independently). {@code replication_progress}
 * is added to the cleanup list because the multi-building scenarios populate it
 * and it is not part of {@link ContractTestBase}'s cleanup.</p>
 *
 * <p><b>Schedulers:</b> {@code app.sync-interval-ms},
 * {@code app.health.monitor-interval-ms} and
 * {@code app.reconciliation.interval-ms} are all {@code 999999999} in
 * {@code application-test.yml}, so the periodic tasks do NOT auto-fire. Scenarios
 * invoke {@code replicateUsers()} directly on the autowired
 * {@link UserReplicationSchedulerService} bean. The method is synchronous
 * (C-R4: {@code CompletableFuture.allOf().join()}), so assertions run
 * immediately after the call returns — no Awaitility (not on the central
 * classpath) and no {@code Thread.sleep} needed.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class MultiBuildingTestBase {

    protected static final String SYNC_PATH_1 = "/building-1/internal/users/sync";
    protected static final String SYNC_PATH_2 = "/building-2/internal/users/sync";
    protected static final String SYNC_PATH_3 = "/building-3/internal/users/sync";

    protected WireMockServer wireMockServer;
    protected JdbcTemplate jdbcTemplate;
    protected String baseUrl1;
    protected String baseUrl2;
    protected String baseUrl3;

    @Autowired
    protected LocalServerRegistryPort localServerRegistryPort;

    @Autowired
    protected UserReplicationSchedulerService userReplicationSchedulerService;

    @Autowired
    protected ReceiveSyncDataUseCase receiveSyncDataUseCase;

    @Autowired
    protected OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    protected ReplicationProgressJpaRepository replicationProgressJpaRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    public void setJdbcTemplate(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void baseSetUp() {
        // Wipe all central tables between tests so each @Test starts from a known
        // empty state. Order is irrelevant — none of the central entities declare
        // FK constraints, so plain DELETEs are safe. replication_progress is added
        // because the multi-building scenarios populate it.
        jdbcTemplate.execute("DELETE FROM replication_progress");
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM aggregated_statistics");
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM local_servers");
        jdbcTemplate.execute("DELETE FROM users");

        // Single WireMock server, random port — three URL prefixes for the three
        // buildings so each building's baseUrl resolves to a distinct stub on the
        // same server.
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        int port = wireMockServer.port();
        baseUrl1 = "http://localhost:" + port + "/building-1";
        baseUrl2 = "http://localhost:" + port + "/building-2";
        baseUrl3 = "http://localhost:" + port + "/building-3";

        // LocalServerRestAdapter issues PUT <baseUrl>/internal/users/sync with the
        // M3 ack contract as the response body.
        stubUserSync(SYNC_PATH_1);
        stubUserSync(SYNC_PATH_2);
        stubUserSync(SYNC_PATH_3);
        // Defensive count stubs (only relevant if a scenario invokes the count port).
        stubUserCount("/building-1/internal/users/count");
        stubUserCount("/building-2/internal/users/count");
        stubUserCount("/building-3/internal/users/count");
    }

    @AfterEach
    void baseTearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    private void stubUserSync(String path) {
        wireMockServer.stubFor(put(urlEqualTo(path))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"userId\":\"any\",\"applied\":true,\"reason\":null}]")));
    }

    private void stubUserCount(String path) {
        wireMockServer.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("0")));
    }

    /**
     * Registers a building through the REAL {@link LocalServerRegistryPort}
     * (the H2-backed {@code LocalServerRepositoryAdapter} bean). The adapter's
     * {@code @Transactional} proxy commits the registration and fires the M8
     * {@code afterCommit} catch-up synchronously before this method returns — so
     * by the time the caller inspects state, the registration is durable and the
     * catch-up (a no-op when the outbox is empty) has already run. No
     * {@code TransactionTemplate} / manual
     * {@code TransactionSynchronizationManager} dance is needed: the real
     * Spring proxy handles the transaction and the {@code afterCommit} hook.
     */
    protected void registerBuilding(String buildingId, String baseUrl) {
        localServerRegistryPort.register(
                new RegisteredLocalServer(new BuildingId(buildingId), baseUrl, Instant.now(), true));
    }

    /**
     * Number of {@code PUT /internal/users/sync} calls WireMock has served for
     * the given building's URL path. Used to assert push counts per building.
     */
    protected int putSyncCountFor(String path) {
        return wireMockServer.findAll(putRequestedFor(urlEqualTo(path))).size();
    }
}
