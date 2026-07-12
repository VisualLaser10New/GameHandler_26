package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;
import java.util.Map;

/**
 * Outbox payload for the {@code GAME_DEFINITION_UPSERT_REQUESTED} event emitted
 * by a Local Server GAME_ADMIN use case (PIANO §7.B W12) and consumed by the
 * Central {@code SyncEventProcessor} §7.A.7 branch
 * {@code GAME_DEFINITION_UPSERT_REQUESTED}, which delegates to
 * {@code UpsertGameDefinitionUseCase.upsert(gameDefinition, originatingRequestId)}.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId}; the Central
 * return event ({@code GAME_DEFINITION_UPSERTED}) carries it back as
 * {@code originatingRequestId} so the Local can {@code markCompleted}.</p>
 *
 * @param eventId            the Local outbox event id (UUID)
 * @param eventType          always {@code GAME_DEFINITION_UPSERT_REQUESTED}
 * @param requestId          the admin-request id (== {@code eventId})
 * @param actingUserId       the admin user id (GAME_ADMIN) requesting the change
 * @param actingRole         the role of the acting admin
 * @param buildingId         the building where the admin is connected
 * @param gameType           the game type to upsert
 * @param name               the game display name
 * @param minPlayers         the minimum player count
 * @param maxPlayers         the maximum player count
 * @param teamAllowed        whether team-based play is allowed
 * @param registrationRules  the registration rules map
 * @param createdAt          the request creation instant
 */
public record GameDefinitionUpsertRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        GameType gameType,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean teamAllowed,
        Map<String, Object> registrationRules,
        Instant createdAt
) {
}