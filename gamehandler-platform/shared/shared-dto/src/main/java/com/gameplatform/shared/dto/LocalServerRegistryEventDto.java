package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Outbox payload for the {@code LOCAL_SERVER_REGISTRY_UPSERTED} event in the
 * Central→Local replication flow (PIANO §7.A.3). Carries a single registered
 * local server row so the local node can upsert its
 * {@code registered_local_servers_local} projection (idempotent by PK
 * {@code buildingId}). This lets a PLATFORM_ADMIN client, connected to any
 * Local, see the full registry of active/inactive servers without a direct
 * Central call (E1).
 *
 * <p>{@code originatingRequestId} is nullable: registry events are raised by
 * the Central {@code LocalServerRegistryPort.register} path, where it is
 * {@code null} (no admin request is being closed).</p>
 *
 * @param eventId              outbox event id (UUID)
 * @param eventType            always {@code LOCAL_SERVER_REGISTRY_UPSERTED}
 * @param buildingId           the server building id (PK)
 * @param baseUrl              the server base URL
 * @param lastSeenAt           the last heartbeat instant
 * @param active               whether the server is currently active
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param updatedAt            last mutation instant
 */
public record LocalServerRegistryEventDto(
        String eventId,
        String eventType,
        String buildingId,
        String baseUrl,
        Instant lastSeenAt,
        boolean active,
        String originatingRequestId,
        Instant updatedAt
) {
    public LocalServerRegistryEventDto(String eventId, String eventType, String buildingId,
                                      String baseUrl, Instant lastSeenAt, boolean active, Instant updatedAt) {
        this(eventId, eventType, buildingId, baseUrl, lastSeenAt, active, null, updatedAt);
    }
}