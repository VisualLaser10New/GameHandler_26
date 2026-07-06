package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.in.GetAllUsersUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.central.domain.ports.out.QueryLocalServerUserCountPort;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * M4 — periodic reconciliation between the central users table and the
 * {@code replicated_users} table held by each active local server.
 *
 * <p>Every hour (default {@code app.reconciliation.interval-ms:3600000}) the
 * service iterates over the active local servers (the same
 * {@link LocalServerRegistryPort#getActiveLocalServers()} set used by the
 * replication scheduler — inactive servers are intentionally skipped because
 * re-pushing to a deactivated server would re-trigger replication and the M13
 * behavioural consequence says inactive servers don't receive pushes). For
 * each server it:</p>
 * <ol>
 *   <li>queries the local {@code replicated_users} count via
 *       {@link QueryLocalServerUserCountPort} (returns
 *       {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE} on failure);</li>
 *   <li>fetches the central user snapshot once via
 *       {@link GetAllUsersUseCase#getAllUsersForSync()} (returns ALL central
 *       users with email + occurredAt populated — R4); the central count is
 *       the snapshot size;</li>
 *   <li>if counts match → logs INFO and continues to the next server;</li>
 *   <li>if counts mismatch → re-pushes the full central snapshot to that
 *       server as a SINGLE batch via
 *       {@link PushUserToLocalServersPort#pushUsers(List, RegisteredLocalServer)}
 *       and logs WARN with the delta. The local R2 ordering guard
 *       ({@code UserSyncService} eventTime + {@code @Version}) makes a full
 *       re-push idempotent — a stale per-user upsert is simply skipped by the
 *       local side.</li>
 * </ol>
 *
 * <p><b>Per-server isolation:</b> each server is reconciled inside its own
 * try/catch — a count-port exception, a push exception, or any other failure
 * for one server is logged WARN and the loop moves on to the next server. A
 * single failing server never aborts the sweep.</p>
 *
 * <p><b>Design decision — SKIP per-event {@code replication_progress} recording
 * (M4-specific):</b> unlike {@link LateRegistrationCatchUpService} (R1+M3) which
 * replays REAL outbox events and records one {@code replication_progress} row
 * per (event_id, server_id), M4 reconciliation does NOT write any
 * {@code replication_progress} row. The rationale:</p>
 * <ul>
 *   <li>The {@code replication_progress} table is keyed by the central outbox
 *       {@code event_id}. M4 re-pushes the CURRENT user snapshot from
 *       {@code getAllUsersForSync()} WITHOUT creating any new outbox event —
 *       there is no business event to record, only a sync correction.</li>
 *   <li>The {@link UserSyncDto} does not carry an {@code eventId}; the same
 *       user can be the subject of multiple historical outbox events, so
 *       mapping a re-push back to a specific event would be ambiguous.</li>
 *   <li>Writing fake progress rows would corrupt the
 *       {@link com.gameplatform.central.domain.ports.out.OutboxEventRepository#countPendingReplicationForServer(String)}
 *       backlog metric (M12) used by the admin health view.</li>
 *   <li>Idempotency is already guaranteed by the local R2 ordering guard, so
 *       the progress table is not needed for correctness here.</li>
 * </ul>
 * <p>This is the documented deviation from a strict "reuse catch-up's per-event
 * progress pattern" reading of the plan: the catch-up flow needs progress
 * rows because it replays real outbox events and must avoid re-replaying them
 * on the next registration; M4 is a stateless count diff + re-push, so it
 * does not.</p>
 *
 * <p><b>Email backfill (R4 support):</b> M4 doubles as the backfill mechanism
 * for R4's {@code email} column on existing pre-R4 local rows. A re-push
 * triggered by a count mismatch re-sends every central user (with email) via
 * the local upsert path; pre-R4 rows that were missing the email column get
 * it populated by the upsert. <em>Important caveat:</em> the count comparator
 * counts ROWS, not columns — a pre-R4 local row missing email still counts
 * as 1 row, so if every user row already exists on the local side the count
 * matches and M4 will NOT re-push. For email backfill on a system that has
 * no row-count drift, the operator must either (a) lower
 * {@code app.reconciliation.interval-ms} and force a mismatch (e.g. by
 * deleting one local row), or (b) trigger the reconciliation manually. See
 * the report's "Critical guidance for the Empirical-gap agent" section.</p>
 *
 * <p><b>Scheduler thread:</b> the {@code @Scheduled} method resolves to the
 * shared {@code taskScheduler} bean (C-R4's
 * {@link com.gameplatform.central.infrastructure.config.SchedulerConfig#taskScheduler}).
 * Per the M4 plan, the per-server re-push runs sequentially on the scheduler
 * thread (no dedicated executor) because the hourly cadence is far longer
 * than the bounded ~30 s per-server REST timeout — there is no concurrency
 * concern at hourly cadence and the simpler sequential form is easier to
 * reason about. Each server is bounded by the
 * {@code central.replication.read-timeout-ms} (default 5 s) × 3 retry
 * attempts × (count + push) calls ≈ 30 s worst case.</p>
 */
@Service
public class UserReplicationReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(UserReplicationReconciliationService.class);

    private final LocalServerRegistryPort localServerRegistryPort;
    private final QueryLocalServerUserCountPort queryLocalServerUserCountPort;
    private final GetAllUsersUseCase getAllUsersUseCase;
    private final PushUserToLocalServersPort pushUserToLocalServersPort;
    private final Clock clock;

    public UserReplicationReconciliationService(LocalServerRegistryPort localServerRegistryPort,
                                                 QueryLocalServerUserCountPort queryLocalServerUserCountPort,
                                                 GetAllUsersUseCase getAllUsersUseCase,
                                                 PushUserToLocalServersPort pushUserToLocalServersPort,
                                                 Clock clock) {
        this.localServerRegistryPort = localServerRegistryPort;
        this.queryLocalServerUserCountPort = queryLocalServerUserCountPort;
        this.getAllUsersUseCase = getAllUsersUseCase;
        this.pushUserToLocalServersPort = pushUserToLocalServersPort;
        this.clock = clock;
    }

    /**
     * Hourly reconciliation between central users and each active local
     * server's {@code replicated_users} table.
     *
     * <p>{@code fixedDelay} ensures no overlapping runs — the next hourly
     * window starts only after the previous sweep completes. The default
     * 1 h interval is overridden to {@code 999999999} in
     * {@code application-test.yml} to prevent the reconciliation from firing
     * during central integration tests.</p>
     *
     * <p>{@code initialDelay} matches {@code fixedDelay} (same value) so the
     * first sweep runs at T+1h, NOT at startup. Rationale: at T0 the local
     * servers may not have registered yet (central typically starts before
     * them), so an immediate reconcile would fail to reach them and log a
     * noisy {@code Reconciliation skipped ... count unavailable (-1)} line.
     * The primary recovery paths do NOT depend on reconcile firing at T0:
     * the 5-min {@link UserReplicationSchedulerService} keeps active servers
     * in sync, and {@link LateRegistrationCatchUpService} catch-up (now also
     * fired on reactivation) replays any SENT events a returning server
     * missed. Reconcile remains the hourly belt-and-suspenders backstop.
     * This mirrors {@code LocalServerHealthMonitorService}'s
     * {@code initialDelay == fixedDelay} scheduling pattern.</p>
     */
    @Scheduled(
            fixedDelayString = "${app.reconciliation.interval-ms:3600000}",
            initialDelayString = "${app.reconciliation.interval-ms:3600000}")
    public void reconcile() {
        List<RegisteredLocalServer> activeServers = localServerRegistryPort.getActiveLocalServers();
        if (activeServers.isEmpty()) {
            log.debug("Reconciliation: no active local servers; nothing to reconcile at {}.",
                    java.time.Instant.now(clock));
            return;
        }

        // Fetch the central snapshot once and reuse for every server — saves N-1 DB calls.
        List<UserSyncDto> centralUsers = getAllUsersUseCase.getAllUsersForSync();
        int centralCount = centralUsers.size();

        for (RegisteredLocalServer server : activeServers) {
            String buildingId = server.getBuildingId().id();
            try {
                long localCount = queryLocalServerUserCountPort.countReplicatedUsers(server);

                if (localCount == QueryLocalServerUserCountPort.COUNT_UNAVAILABLE) {
                    // Count query failed (logged by the adapter). SKIP this server this cycle.
                    log.warn("Reconciliation skipped for buildingId={}: local count unavailable ({}).",
                            buildingId, QueryLocalServerUserCountPort.COUNT_UNAVAILABLE);
                    continue;
                }

                if (localCount != centralCount) {
                    // Mismatch → re-push the full snapshot as a single batch.
                    // The local R2 ordering guard (eventTime + @Version) makes this idempotent.
                    pushUserToLocalServersPort.pushUsers(centralUsers, server);
                    log.warn("Reconciliation mismatch for buildingId={}: centralCount={}, localCount={}; re-pushed {} users",
                            buildingId, centralCount, localCount, centralCount);
                } else {
                    log.info("Reconciliation OK for buildingId={}: centralCount={}, localCount={}",
                            buildingId, centralCount, localCount);
                }
            } catch (Exception e) {
                // Per-server isolation: log and continue to the next server.
                // A push failure (RuntimeException from LocalServerRestAdapter after retries)
                // lands here — the sweep must NOT abort because of one server.
                log.warn("Reconciliation failed for buildingId={}: {}", buildingId, e.getMessage(), e);
            }
        }
    }
}