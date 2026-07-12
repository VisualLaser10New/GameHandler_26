package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Outbox payload and sync batch element for the LOCAL_ADMIN↔building metadata
 * replication flow (Central → Local).
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code LOCAL_ADMIN_BUILDING_ASSIGNED} — upsert the binding on the local;
 *       {@code assignedAt} is the assignment timestamp.</li>
 *   <li>{@code LOCAL_ADMIN_BUILDING_REVOKED} — delete the binding on the local;
 *       {@code assignedAt} is null (and ignored).</li>
 * </ul>
 *
 * <p>{@code eventId} is the outbox event id (UUID) so the local can dedupe /
 * the central can track {@code replication_progress}.</p>
 *
 * @param eventId    outbox event id (UUID)
 * @param eventType  one of {@code LOCAL_ADMIN_BUILDING_ASSIGNED}, {@code LOCAL_ADMIN_BUILDING_REVOKED}
 * @param userId     the LOCAL_ADMIN user id
 * @param buildingId the building id
 * @param assignedAt assignment timestamp (null for revoke events)
 */
public record LocalAdminBuildingEventDto(
        String eventId,
        String eventType,
        String userId,
        String buildingId,
        Instant assignedAt
) {
}