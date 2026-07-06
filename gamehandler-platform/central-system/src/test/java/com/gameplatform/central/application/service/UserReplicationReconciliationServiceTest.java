package com.gameplatform.central.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.in.GetAllUsersUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.QueryLocalServerUserCountPort;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4 — Mockito unit tests for {@link UserReplicationReconciliationService}.
 *
 * <p>Covers the four contract scenarios from the plan:</p>
 * <ul>
 *   <li>match (counts equal) → INFO log, NO re-push;</li>
 *   <li>mismatch (counts differ) → re-push called with the central user list,
 *       WARN log;</li>
 *   <li>count-port exception for one server → logged WARN + skipped, sweep
 *       continues for subsequent servers;</li>
 *   <li>empty active-server list → no-op (no count query, no push, no
 *       getAllUsersForSync).</li>
 * </ul>
 * Plus the per-server isolation guarantee: one server's failure does NOT abort
 * the sweep for the others.
 *
 * <p>Logs are captured via a Logback {@link ListAppender} attached to the
 * service's logger (LogCaptor is forbidden on this project).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserReplicationReconciliationService")
class UserReplicationReconciliationServiceTest {

    /** Fixed now: 2026-07-05T12:00:00Z. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC);

    @Mock private LocalServerRegistryPort localServerRegistryPort;
    @Mock private QueryLocalServerUserCountPort queryLocalServerUserCountPort;
    @Mock private GetAllUsersUseCase getAllUsersUseCase;
    @Mock private PushUserToLocalServersPort pushUserToLocalServersPort;

    private UserReplicationReconciliationService service;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new UserReplicationReconciliationService(
                localServerRegistryPort,
                queryLocalServerUserCountPort,
                getAllUsersUseCase,
                pushUserToLocalServersPort,
                FIXED_CLOCK);

        logger = (Logger) LoggerFactory.getLogger(UserReplicationReconciliationService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    private RegisteredLocalServer server(String buildingId) {
        return new RegisteredLocalServer(
                new BuildingId(buildingId),
                "http://" + buildingId + ":8081",
                Instant.parse("2026-07-05T11:00:00Z"),
                true);
    }

    private UserSyncDto user(String id, String username) {
        return new UserSyncDto(id, username, "mail-" + id + "@ex.com", "hash",
                List.of("USER"), Instant.parse("2026-07-05T10:00:00Z"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Match: counts equal → INFO log, no re-push
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("match: when localCount == centralCount, logs INFO and does NOT re-push")
    void matchLogsInfoAndDoesNotRePush() {
        RegisteredLocalServer s = server("building-1");
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(s));
        List<UserSyncDto> centralUsers = List.of(user("u1", "alice"), user("u2", "bob"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);
        when(queryLocalServerUserCountPort.countReplicatedUsers(s)).thenReturn(2L);

        service.reconcile();

        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation OK")
                        .contains("buildingId=building-1")
                        .contains("centralCount=2")
                        .contains("localCount=2"));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN || ev.getLevel() == Level.ERROR)
                .isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mismatch: counts differ → re-push + WARN log
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mismatch: when localCount != centralCount, re-pushes the full central snapshot (single batch) and logs WARN")
    void mismatchRePushesFullSnapshotAndLogsWarn() {
        RegisteredLocalServer s = server("building-1");
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of(s));
        List<UserSyncDto> centralUsers = List.of(
                user("u1", "alice"), user("u2", "bob"), user("u3", "carol"),
                user("u4", "dave"), user("u5", "eve"), user("u6", "frank"),
                user("u7", "grace"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);
        when(queryLocalServerUserCountPort.countReplicatedUsers(s)).thenReturn(5L);

        service.reconcile();

        // Re-pushed as a SINGLE batch with the FULL central user list.
        verify(pushUserToLocalServersPort, times(1)).pushUsers(eq(centralUsers), eq(s));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation mismatch")
                        .contains("buildingId=building-1")
                        .contains("centralCount=7")
                        .contains("localCount=5")
                        .contains("re-pushed 7"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Count-port exception for ONE server: logged WARN + skipped, sweep continues
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("count-port exception for one server logs WARN and continues with the next server")
    void countPortExceptionForOneServerIsLoggedAndSkipped() {
        RegisteredLocalServer failing = server("building-failing");
        RegisteredLocalServer healthy = server("building-healthy");
        when(localServerRegistryPort.getActiveLocalServers())
                .thenReturn(List.of(failing, healthy));
        List<UserSyncDto> centralUsers = List.of(user("u1", "alice"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);

        // First server throws on the count query.
        when(queryLocalServerUserCountPort.countReplicatedUsers(failing))
                .thenThrow(new RuntimeException("connection refused"));
        // Second server returns a matching count → no re-push.
        when(queryLocalServerUserCountPort.countReplicatedUsers(healthy)).thenReturn(1L);

        service.reconcile();

        // Healthy server flowed through to the OK branch — no push, INFO log.
        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation failed")
                        .contains("building-failing"));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation OK")
                        .contains("building-healthy"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Count unavailable (-1): skip this server this cycle, no re-push
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("COUNT_UNAVAILABLE from count port logs WARN and skips this server (no re-push)")
    void countUnavailableSkipsServerAndContinues() {
        RegisteredLocalServer unreachable = server("building-down");
        RegisteredLocalServer ok = server("building-ok");
        when(localServerRegistryPort.getActiveLocalServers())
                .thenReturn(List.of(unreachable, ok));
        List<UserSyncDto> centralUsers = List.of(user("u1", "alice"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);

        when(queryLocalServerUserCountPort.countReplicatedUsers(unreachable))
                .thenReturn(QueryLocalServerUserCountPort.COUNT_UNAVAILABLE);
        when(queryLocalServerUserCountPort.countReplicatedUsers(ok)).thenReturn(1L);

        service.reconcile();

        // The unreachable server is skipped — no push to it.
        // The OK server matches → no push to it either.
        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation skipped")
                        .contains("building-down")
                        .contains("local count unavailable"));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation OK")
                        .contains("building-ok"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Empty active-server list: no-op
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("empty active-server list: no count query, no push, no getAllUsersForSync (early return)")
    void emptyActiveServerListIsNoOp() {
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        service.reconcile();

        verify(queryLocalServerUserCountPort, never()).countReplicatedUsers(any());
        verify(pushUserToLocalServersPort, never()).pushUsers(any(), any());
        verify(getAllUsersUseCase, never()).getAllUsersForSync();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Push failure for one server: logged WARN, sweep continues (per-server isolation)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pushUsers exception for one server logs WARN and continues with the next server")
    void pushFailureForOneServerIsLoggedAndContinues() {
        RegisteredLocalServer failing = server("building-push-fail");
        RegisteredLocalServer healthy = server("building-push-ok");
        when(localServerRegistryPort.getActiveLocalServers())
                .thenReturn(List.of(failing, healthy));
        List<UserSyncDto> centralUsers = List.of(user("u1", "alice"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);

        // Both servers mismatch → both trigger a re-push.
        when(queryLocalServerUserCountPort.countReplicatedUsers(failing)).thenReturn(0L);
        when(queryLocalServerUserCountPort.countReplicatedUsers(healthy)).thenReturn(0L);

        // First push throws (LocalServerRestAdapter wraps runtime exception after retries).
        when(pushUserToLocalServersPort.pushUsers(centralUsers, failing))
                .thenThrow(new RuntimeException("Failed to push users to local server: http://building-push-fail:8081"));
        // Second push succeeds — sweep continues despite the first failure.
        when(pushUserToLocalServersPort.pushUsers(centralUsers, healthy)).thenReturn(List.of());

        service.reconcile();

        // Both pushes were attempted — the failure on the first did NOT abort the sweep.
        verify(pushUserToLocalServersPort, times(1)).pushUsers(centralUsers, failing);
        verify(pushUserToLocalServersPort, times(1)).pushUsers(centralUsers, healthy);

        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation failed")
                        .contains("building-push-fail"));
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Reconciliation mismatch")
                        .contains("building-push-ok"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getAllUsersForSync is called ONCE per sweep, not per server (perf invariant)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsersForSync is fetched ONCE per sweep and reused across all servers")
    void centralSnapshotFetchedOncePerSweep() {
        RegisteredLocalServer s1 = server("building-1");
        RegisteredLocalServer s2 = server("building-2");
        RegisteredLocalServer s3 = server("building-3");
        when(localServerRegistryPort.getActiveLocalServers())
                .thenReturn(List.of(s1, s2, s3));
        List<UserSyncDto> centralUsers = List.of(user("u1", "alice"), user("u2", "bob"));
        when(getAllUsersUseCase.getAllUsersForSync()).thenReturn(centralUsers);
        when(queryLocalServerUserCountPort.countReplicatedUsers(any())).thenReturn(2L);

        service.reconcile();

        // The snapshot is fetched exactly once even though three servers are reconciled.
        verify(getAllUsersUseCase, times(1)).getAllUsersForSync();
        verify(queryLocalServerUserCountPort, times(3)).countReplicatedUsers(any());
    }
}