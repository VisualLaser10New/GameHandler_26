package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Read-model projection of a Local admin-request row for the
 * {@code GET /api/admin/requests[?requestId=]} Local endpoint
 * (PIANO §7.B). Sourced from the {@code admin_requests_local} table;
 * the {@code payload} and {@code resultData} columns are returned as
 * opaque {@link String} values (the JSON-encoded payload as written by
 * the W use case that created the request) so the client can interpret
 * them without depending on the request-specific DTO contract.
 *
 * @param requestId       the admin-request id (== outbox eventId)
 * @param eventType       the {@code *_REQUESTED} event type emitted
 * @param actingUserId    the admin/PLAYER user id that opened the request
 * @param actingRole      the role of the acting user
 * @param buildingId      the building where the user is connected
 * @param payload         the request payload (JSON string)
 * @param status          the request status (PENDING / COMPLETED / FAILED)
 * @param resultData      the result data (JSON string, nullable)
 * @param createdAt       the request creation instant
 * @param completedAt     the request completion instant (nullable while PENDING)
 * @param outboxEventId   the outbox event id carrying the request (== requestId)
 */
public record AdminRequestDto(
        String requestId,
        String eventType,
        String actingUserId,
        String actingRole,
        String buildingId,
        String payload,
        String status,
        String resultData,
        Instant createdAt,
        Instant completedAt,
        String outboxEventId
) {
}
