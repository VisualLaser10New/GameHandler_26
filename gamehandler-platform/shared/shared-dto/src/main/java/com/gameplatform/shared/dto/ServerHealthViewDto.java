package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated Local server health view for the
 * {@code GET /api/admin/servers/health} PLATFORM_ADMIN endpoint
 * (PIANO §7.B). Combines the local node's own pending-outbox count with
 * the registry of all known registered local servers (replicated via
 * {@code LOCAL_SERVER_REGISTRY_UPSERTED} into
 * {@code registered_local_servers_local}).
 *
 * @param myBuildingId           the building id of the responding Local node
 * @param myServerActive         whether the responding Local node is active
 * @param myLastSeenAt           the responding Local node's last heartbeat instant
 * @param myPendingOutboxCount   the count of PENDING rows on the local outbox
 * @param registeredServers      the full registry of local servers
 */
public record ServerHealthViewDto(
        String myBuildingId,
        boolean myServerActive,
        Instant myLastSeenAt,
        long myPendingOutboxCount,
        List<ServerHealthDto> registeredServers
) {
}
