package com.gameplatform.central.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.shared.domain.model.BuildingId;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M13 — focused Mockito unit tests for {@link LocalServerHealthMonitorService}.
 *
 * <p>Asserts the monitor:
 * <ul>
 *   <li>deactivates ONLY servers that are active AND stale
 *       ({@code lastSeenAt < now − threshold}),</li>
 *   <li>leaves active-but-recent servers untouched,</li>
 *   <li>leaves already-inactive servers untouched (idempotent skip),</li>
 *   <li>logs an INFO line per deactivation.</li>
 * </ul>
 * Logs are captured via a Logback {@link ListAppender} attached to the
 * service's logger (LogCaptor is forbidden on this project).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalServerHealthMonitorService")
class LocalServerHealthMonitorServiceTest {

    /** Fixed now: 2026-07-05T12:00:00Z. */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC);
    private static final long THRESHOLD_MS = 15L * 60_000L; // 15 min

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    private LocalServerHealthMonitorService service;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        service = new LocalServerHealthMonitorService(localServerRegistryPort, FIXED_CLOCK, THRESHOLD_MS);

        logger = (Logger) LoggerFactory.getLogger(LocalServerHealthMonitorService.class);
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

    private RegisteredLocalServer server(String buildingId, Instant lastSeenAt, boolean active) {
        return new RegisteredLocalServer(new BuildingId(buildingId),
                "http://" + buildingId + ":8081", lastSeenAt, active);
    }

    @Test
    @DisplayName("deactivates active-stale server, leaves active-recent and inactive servers untouched")
    void deactivatesOnlyActiveStale() {
        // now = 12:00:00, threshold = 15 min → cut-off = 11:45:00
        Instant now = Instant.now(FIXED_CLOCK);
        Instant recent = now.minusSeconds(60);           // 11:59:00 — recent
        Instant stale = now.minusSeconds(30 * 60);       // 11:30:00 — stale
        Instant inactiveRecent = now.minusSeconds(60);   // recent timestamp on inactive server

        RegisteredLocalServer activeRecent = server("building-active-recent", recent, true);
        RegisteredLocalServer activeStale = server("building-active-stale", stale, true);
        RegisteredLocalServer alreadyInactive = server("building-inactive", inactiveRecent, false);

        when(localServerRegistryPort.findAll())
                .thenReturn(List.of(activeRecent, activeStale, alreadyInactive));

        service.monitor();

        // Only the active-stale server is deactivated.
        verify(localServerRegistryPort).deactivate(new BuildingId("building-active-stale"));
        verify(localServerRegistryPort, times(1)).deactivate(any(BuildingId.class));

        // An INFO log line was emitted for the deactivation.
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.INFO)
                .anySatisfy(ev -> assertThat(ev.getFormattedMessage())
                        .contains("Deactivating stale local server")
                        .contains("building-active-stale"));

        // No WARN/ERROR.
        assertThat(listAppender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN || ev.getLevel() == Level.ERROR)
                .isEmpty();
    }

    @Test
    @DisplayName("monitors a no-op when no servers are registered")
    void noOpWhenNoServers() {
        when(localServerRegistryPort.findAll()).thenReturn(List.of());

        service.monitor();

        verify(localServerRegistryPort, never()).deactivate(any(BuildingId.class));

        // No INFO log emitted (the early-return path produces nothing).
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    @DisplayName("does NOT deactivate an active server exactly at the threshold boundary")
    void boundaryServerAtExactThresholdIsNotStale() {
        // now = 12:00:00 — 15 min = 11:45:00. A server with lastSeenAt = 11:45:00
        // is NOT before the threshold (exclusive), so it must NOT be deactivated.
        Instant boundary = Instant.now(FIXED_CLOCK).minusMillis(THRESHOLD_MS);
        RegisteredLocalServer boundaryServer = server("building-boundary", boundary, true);

        when(localServerRegistryPort.findAll()).thenReturn(List.of(boundaryServer));

        service.monitor();

        verify(localServerRegistryPort, never()).deactivate(any(BuildingId.class));
    }

    @Test
    @DisplayName("deactivates multiple stale servers in a single pass")
    void deactivatesMultipleStaleServers() {
        Instant stale1 = Instant.now(FIXED_CLOCK).minusSeconds(40 * 60); // 11:20:00
        Instant stale2 = Instant.now(FIXED_CLOCK).minusSeconds(20 * 60); // 11:40:00 — still before 11:45:00

        RegisteredLocalServer s1 = server("building-a", stale1, true);
        RegisteredLocalServer s2 = server("building-b", stale2, true);

        when(localServerRegistryPort.findAll()).thenReturn(List.of(s1, s2));

        service.monitor();

        verify(localServerRegistryPort).deactivate(new BuildingId("building-a"));
        verify(localServerRegistryPort).deactivate(new BuildingId("building-b"));
        verify(localServerRegistryPort, times(2)).deactivate(any(BuildingId.class));
    }

    @Test
    @DisplayName("skips a stale server whose isActive is already false (idempotent)")
    void staleButAlreadyInactiveIsSkipped() {
        Instant stale = Instant.now(FIXED_CLOCK).minusSeconds(30 * 60);
        RegisteredLocalServer inactiveStale = server("building-z", stale, false);

        when(localServerRegistryPort.findAll()).thenReturn(List.of(inactiveStale));

        service.monitor();

        verify(localServerRegistryPort, never()).deactivate(any(BuildingId.class));
    }
}