package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;

/**
 * M4 — outbound port used by the periodic reconciliation
 * ({@code UserReplicationReconciliationService}) to query how many rows the
 * given local server currently holds in its {@code replicated_users} table.
 *
 * <p>The returned {@code long} is compared against the central user count
 * (from {@code UserService#getAllUsersForSync()}) to decide whether the
 * reconciliation must re-push the full user snapshot to that server.</p>
 *
 * <p><b>Failure contract:</b> implementations MUST return {@code -1L} when the
 * count cannot be retrieved (transient network failure after exhausting
 * retries, non-2xx response, or any exception). The service treats
 * {@code -1L} as "unknown" — it logs a WARN and SKIPS that server this cycle
 * (a re-push triggered by an unreachable server every hour would be wasteful
 * and could pile up if the server is down for an extended period).</p>
 */
public interface QueryLocalServerUserCountPort {

    /**
     * Sentinel returned by {@link #countReplicatedUsers(RegisteredLocalServer)}
     * when the count cannot be retrieved after exhausting retries (server down,
     * non-2xx, transient network failure). The service treats this as "unknown"
     * and SKIPS that server for the current cycle — a re-push triggered by an
     * unreachable server every hour would be wasteful and could pile up if the
     * server is down for an extended period. Defined on the port (not on the
     * adapter) so the application service can reference it without depending on
     * the infrastructure layer (hexagonal dependency rule).
     */
    long COUNT_UNAVAILABLE = -1L;

    long countReplicatedUsers(RegisteredLocalServer server);
}