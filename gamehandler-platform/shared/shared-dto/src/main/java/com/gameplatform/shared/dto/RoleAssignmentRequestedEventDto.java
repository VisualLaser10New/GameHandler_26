package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Outbox payload for the {@code ROLE_ASSIGNMENT_REQUESTED} event emitted by a
 * Local Server admin use case (PIANO §7.B W12 / RF-UT-02) and consumed by the
 * Central {@code SyncEventProcessor} §7.A.7 branch
 * {@code ROLE_ASSIGNMENT_REQUESTED}, which delegates to
 * {@code UpdateUserUseCase.updateUser(targetUserId, null, roles, originatingRequestId)}.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId} (and the
 * {@code admin_requests_local.requestId}); the Central return event
 * ({@code USER_UPDATED}) carries it back as {@code originatingRequestId} so the
 * Local can {@code markCompleted} the admin request.</p>
 *
 * @param eventId        the Local outbox event id (UUID)
 * @param eventType      always {@code ROLE_ASSIGNMENT_REQUESTED}
 * @param requestId      the admin-request id (== {@code eventId})
 * @param actingUserId   the admin user id (PLATFORM_ADMIN) requesting the change
 * @param actingRole     the role of the acting admin
 * @param buildingId     the building where the admin is connected
 * @param targetUserId   the user id whose roles are being assigned
 * @param roles          the new roles to assign (replaces the existing set)
 * @param createdAt      the request creation instant
 */
public record RoleAssignmentRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String targetUserId,
        java.util.List<String> roles,
        Instant createdAt
) {
}