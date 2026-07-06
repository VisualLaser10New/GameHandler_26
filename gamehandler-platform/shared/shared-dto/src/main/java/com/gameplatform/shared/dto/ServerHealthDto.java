package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * M12 — per-server health snapshot returned by the central admin endpoint
 * {@code GET /internal/servers}.
 *
 * @param buildingId             the local server's building identifier
 * @param baseUrl                the local server's base URL
 * @param lastSeenAt             timestamp of the last heartbeat / sync receive
 * @param active                 whether the server is currently considered active
 *                               (flipped to {@code false} by
 *                               {@code LocalServerHealthMonitorService} once the
 *                               server is silent for longer than the stale threshold)
 * @param pendingReplicationCount number of user-replication events still pending
 *                                for this server (USER_REGISTERED / USER_UPDATED
 *                                with status &ne; SENT and no
 *                                {@code replication_progress} row for this server)
 */
public record ServerHealthDto(
        String buildingId,
        String baseUrl,
        Instant lastSeenAt,
        boolean active,
        long pendingReplicationCount
) {}
