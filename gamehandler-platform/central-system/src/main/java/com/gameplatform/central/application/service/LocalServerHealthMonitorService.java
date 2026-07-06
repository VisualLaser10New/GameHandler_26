package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * M13 — scheduled service that monitors the liveness of registered local
 * servers and flips {@code is_active = false} for any server that has been
 * silent for longer than the configurable stale threshold.
 *
 * <p><b>Two-property design (deviation from the plan's single-property
 * conflation):</b> the monitor uses TWO independent {@code app.health.*}
 * properties:
 * <ul>
 *   <li>{@code app.health.monitor-interval-ms} (default {@code 900000} = 15 min)
 *       — drives the {@code @Scheduled fixedDelay}, i.e. how often the monitor
 *       runs. Decoupling this from the threshold lets operators tune the
 *       polling cadence without changing the staleness window (and vice
 *       versa).</li>
 *   <li>{@code app.health.server-stale-threshold-ms} (default {@code 900000}
 *       = 15 min) — drives the staleness cut-off. A server whose
 *       {@code lastSeenAt} is older than {@code now − threshold} is considered
 *       stale. The default is &gt; 2&times; the replication scheduler's
 *       {@code SYNC_INTERVAL_MS = 300000}, so a single missed replication
 *       cycle never falsely deactivates a healthy-but-slow server.</li>
 * </ul>
 * Both default to 15 min as the plan specifies, but they can be tuned
 * independently in production. The shared {@code taskScheduler} bean
 * (C-R4's {@link com.gameplatform.central.infrastructure.config.SchedulerConfig#taskScheduler})
 * backs the {@code @Scheduled} invocation.</p>
 *
 * <p><b>Behavioural consequence (plan §M13):</b> once deactivated, the server
 * is no longer returned by {@link LocalServerRegistryPort#getActiveLocalServers()},
 * so {@code UserReplicationSchedulerService.replicateUsers()} stops pushing to
 * it. Events for that building's users stay PENDING — other active buildings
 * still receive them via per-server {@code replication_progress}. Re-registration
 * via {@code POST /internal/servers/register} re-activates the server (the
 * existing {@code register} flow sets {@code isActive = true}) and triggers the
 * R1 catch-up (M8 afterCommit seam) for any events that accumulated while it
 * was inactive.</p>
 *
 * <p>The monitor method is pure-DB: it loads {@code findAll()} and calls
 * {@code deactivate(BuildingId)} for each stale server. No blocking I/O, so
 * the shared {@code taskScheduler} is sufficient — no dedicated executor
 * needed.</p>
 */
@Service
public class LocalServerHealthMonitorService {

    private static final Logger log = LoggerFactory.getLogger(LocalServerHealthMonitorService.class);

    private final LocalServerRegistryPort localServerRegistryPort;
    private final Clock clock;
    private final long staleThresholdMs;

    public LocalServerHealthMonitorService(LocalServerRegistryPort localServerRegistryPort,
                                           Clock clock,
                                           @Value("${app.health.server-stale-threshold-ms:900000}") long staleThresholdMs) {
        this.localServerRegistryPort = localServerRegistryPort;
        this.clock = clock;
        this.staleThresholdMs = staleThresholdMs;
    }

    /**
     * Periodically scans every registered local server and deactivates the
     * ones whose {@code lastSeenAt} is older than {@code now − threshold}.
     *
     * <p>Only servers that are currently active AND stale are deactivated;
     * inactive servers and active-recent servers are left untouched. The
     * method is idempotent: a server already deactivated is simply skipped
     * on the next run.</p>
     */
    @Scheduled(
            fixedDelayString = "${app.health.monitor-interval-ms:900000}",
            initialDelayString = "${app.health.monitor-interval-ms:900000}")
    public void monitor() {
        Instant threshold = Instant.now(clock).minusMillis(staleThresholdMs);
        List<RegisteredLocalServer> all = localServerRegistryPort.findAll();
        if (all.isEmpty()) {
            return;
        }
        for (RegisteredLocalServer server : all) {
            if (server.isActive() && server.getLastSeenAt() != null
                    && server.getLastSeenAt().isBefore(threshold)) {
                log.info("Deactivating stale local server buildingId={}, lastSeenAt={}",
                        server.getBuildingId().id(), server.getLastSeenAt());
                localServerRegistryPort.deactivate(server.getBuildingId());
            }
        }
    }
}
