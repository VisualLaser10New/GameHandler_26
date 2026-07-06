package com.gameplatform.central.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gameplatform.central.application.service.UserReplicationReconciliationService;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.QueryLocalServerUserCountPort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M4 — H2 integration test for {@link UserReplicationReconciliationService}.
 *
 * <p><b>Downgrade from WireMock to Mockito-only (documented):</b> the plan
 * suggested an optional WireMock-stubbed IT. The existing central integration
 * test infrastructure has no WireMock JUnit extension wired in — adding it
 * would require a new test-scoped dependency + a WireMock @RegisterExtension
 * bootstrap. The M4 contract can be exercised more cheaply by replacing the
 * three outbound ports (registry / count / push) with Mockito mocks inside a
 * {@link TestConfiguration}, while keeping the real
 * {@link com.gameplatform.central.application.service.UserService UserService}
 * bean (which implements {@code GetAllUsersUseCase}) so the reconciliation
 * service reads the REAL central snapshot from H2. This lets us seed central
 * user rows in H2 via {@code userService.register(...)} and verify the service
 * behaviour end-to-end with the real
 * {@link UserReplicationReconciliationService} bean. This is functionally
 * equivalent for the M4 contract (count diff → re-push / no-push) and avoids
 * pulling a new test framework into the build.</p>
 *
 * <p>The IT invokes {@link UserReplicationReconciliationService#reconcile()}
 * directly — the {@code @Scheduled} cadence is disabled in
 * {@code application-test.yml} ({@code app.reconciliation.interval-ms: 999999999}).</p>
 *
 * <p>Logs are captured via a Logback {@link ListAppender} (LogCaptor is
 * forbidden on this project).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(UserReplicationReconciliationIT.TestMocks.class)
@DisplayName("M4: UserReplicationReconciliationService (H2 + Mockito port mocks)")
class UserReplicationReconciliationIT {

    @Autowired
    private com.gameplatform.central.application.service.UserService userService;

    @Autowired
    private UserReplicationReconciliationService reconciliationService;

    @Autowired
    private LocalServerRegistryPort registryMock;
    @Autowired
    private QueryLocalServerUserCountPort countPortMock;
    @Autowired
    private PushUserToLocalServersPort pushPortMock;

    private JdbcTemplate jdbcTemplate;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @Autowired
    void setJdbcTemplate(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(UserReplicationReconciliationService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void cleanUp() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
        // Reset the Mockito stubs between tests (the mocks are shared across the
        // SpringBoot context). The user rows seeded below are also cleared so the
        // next test starts with a known central snapshot.
        org.mockito.Mockito.reset(registryMock, countPortMock, pushPortMock);
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM users");
    }

    private void registerCentralUsers(int n) {
        for (int i = 0; i < n; i++) {
            userService.register("user" + i, "pw", "user" + i + "@ex.com");
        }
    }

    private RegisteredLocalServer server(String buildingId) {
        return new RegisteredLocalServer(
                new BuildingId(buildingId),
                "http://" + buildingId + ":8081",
                Instant.parse("2026-07-05T11:00:00Z"),
                true);
    }

    @Test
    @DisplayName("mismatch on building-1 (central=7, local=5) triggers a single full-batch re-push and a WARN log")
    void mismatchTriggersRePushAndWarnLog() {
        registerCentralUsers(7);

        RegisteredLocalServer building1 = server("building-1");
        when(registryMock.getActiveLocalServers()).thenReturn(List.of(building1));
        when(countPortMock.countReplicatedUsers(building1)).thenReturn(5L);

        reconciliationService.reconcile();

        ArgumentCaptor<List<UserSyncDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushPortMock).pushUsers(captor.capture(), eq(building1));
        assertThat(captor.getValue()).hasSize(7);

        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation mismatch")
                        .contains("buildingId=building-1")
                        .contains("centralCount=7")
                        .contains("localCount=5")
                        .contains("re-pushed 7"));
    }

    @Test
    @DisplayName("match on building-2 (central=3, local=3) logs INFO and does NOT re-push")
    void matchLogsInfoAndDoesNotRePush() {
        registerCentralUsers(3);

        RegisteredLocalServer building2 = server("building-2");
        when(registryMock.getActiveLocalServers()).thenReturn(List.of(building2));
        when(countPortMock.countReplicatedUsers(building2)).thenReturn(3L);

        reconciliationService.reconcile();

        verifyNoInteractions(pushPortMock);
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation OK")
                        .contains("buildingId=building-2")
                        .contains("centralCount=3")
                        .contains("localCount=3"));
    }

    @Test
    @DisplayName("per-server isolation: building-down (count port throws) is skipped, building-ok still reconciled")
    void perServerIsolationContinuesAfterCountPortException() {
        registerCentralUsers(2);

        RegisteredLocalServer down = server("building-down");
        RegisteredLocalServer ok = server("building-ok");
        when(registryMock.getActiveLocalServers()).thenReturn(List.of(down, ok));
        when(countPortMock.countReplicatedUsers(down))
                .thenThrow(new RuntimeException("connection refused"));
        when(countPortMock.countReplicatedUsers(ok)).thenReturn(2L);

        reconciliationService.reconcile();

        // down was skipped (count threw), ok matched → no push fired at all.
        verifyNoInteractions(pushPortMock);
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation failed")
                        .contains("building-down"));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation OK")
                        .contains("building-ok"));
    }

    @Test
    @DisplayName("empty active-server list is a no-op: no count query, no push, no getAllUsersForSync")
    void emptyActiveServerListIsNoOp() {
        when(registryMock.getActiveLocalServers()).thenReturn(List.of());

        reconciliationService.reconcile();

        verifyNoInteractions(countPortMock, pushPortMock);
    }

    /**
     * Mockito-mocked replacements for the three outbound ports used by the
     * reconciliation service. {@code @Primary} overrides the production beans
     * for the duration of the IT. The central {@code UserService} bean (which
     * implements {@code GetAllUsersUseCase}) is NOT replaced — the IT exercises
     * it for real against H2.
     */
    @TestConfiguration
    static class TestMocks {

        @Bean
        @Primary
        LocalServerRegistryPort registryMock() {
            return mock(LocalServerRegistryPort.class);
        }

        @Bean
        @Primary
        QueryLocalServerUserCountPort countPortMock() {
            return mock(QueryLocalServerUserCountPort.class);
        }

        @Bean
        @Primary
        PushUserToLocalServersPort pushPortMock() {
            return mock(PushUserToLocalServersPort.class);
        }
    }
}