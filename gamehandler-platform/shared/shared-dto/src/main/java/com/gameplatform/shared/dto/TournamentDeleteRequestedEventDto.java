package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Outbox payload for the {@code TOURNAMENT_DELETE_REQUESTED} event emitted by a
 * Local Server PLATFORM_ADMIN use case (PIANO §7.B W12) and consumed by the
 * Central {@code SyncEventProcessor} §7.A.7 branch
 * {@code TOURNAMENT_DELETE_REQUESTED}, which delegates to
 * {@code DeleteTournamentUseCase.delete(tournamentId, originatingRequestId)}.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId}; the Central
 * return event ({@code TOURNAMENT_SUMMARY_UPSERTED} tombstone with
 * {@code deleted=true}) carries it back as {@code originatingRequestId} so the
 * Local can {@code markCompleted}.</p>
 *
 * @param eventId        the Local outbox event id (UUID)
 * @param eventType      always {@code TOURNAMENT_DELETE_REQUESTED}
 * @param requestId      the admin-request id (== {@code eventId})
 * @param actingUserId   the admin user id (PLATFORM_ADMIN) requesting the change
 * @param actingRole     the role of the acting admin
 * @param buildingId     the building where the admin is connected
 * @param tournamentId   the target tournament id
 * @param createdAt      the request creation instant
 */
public record TournamentDeleteRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String tournamentId,
        Instant createdAt
) {
}