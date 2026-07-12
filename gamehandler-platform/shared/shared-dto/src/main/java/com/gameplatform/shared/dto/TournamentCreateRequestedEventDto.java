package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code TOURNAMENT_CREATE_REQUESTED} event emitted by a
 * Local Server PLATFORM_ADMIN use case (PIANO §7.B W12) and consumed by the
 * Central {@code SyncEventProcessor} §7.A.7 branch
 * {@code TOURNAMENT_CREATE_REQUESTED}, which delegates to
 * {@code CreateTournamentUseCase.create(tournament, buildingIds, originatingRequestId)}.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId}; the Central
 * return event ({@code TOURNAMENT_SUMMARY_UPSERTED}) carries it back as
 * {@code originatingRequestId} so the Local can {@code markCompleted}.</p>
 *
 * @param eventId        the Local outbox event id (UUID)
 * @param eventType      always {@code TOURNAMENT_CREATE_REQUESTED}
 * @param requestId      the admin-request id (== {@code eventId})
 * @param actingUserId   the admin user id (PLATFORM_ADMIN) requesting the change
 * @param actingRole     the role of the acting admin
 * @param buildingId     the building where the admin is connected
 * @param name           the tournament name
 * @param gameType       the game type
 * @param teamBased      whether the tournament is team-based
 * @param teamSize       the team size (1 for individual)
 * @param startsAt       the scheduled start instant
 * @param buildingIds    the buildings hosting the tournament
 * @param createdAt      the request creation instant
 */
public record TournamentCreateRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        Instant startsAt,
        List<String> buildingIds,
        Instant createdAt
) {
}