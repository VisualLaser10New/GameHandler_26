package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code PARTICIPANT_REGISTER_REQUESTED} event emitted by
 * a Local Server PLAYER use case (PIANO §7.B W6) and consumed by the Central
 * {@code SyncEventProcessor} §7.A.7 branch
 * {@code PARTICIPANT_REGISTER_REQUESTED}, which delegates to
 * {@code RegisterTournamentParticipantUseCase.register(tournamentId, captainId, teamName, teamMemberIds, originatingRequestId)}.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId}; the Central
 * return event ({@code TOURNAMENT_PARTICIPANTS_UPSERTED}) carries it back as
 * {@code originatingRequestId} so the Local can {@code markCompleted}.</p>
 *
 * @param eventId        the Local outbox event id (UUID)
 * @param eventType      always {@code PARTICIPANT_REGISTER_REQUESTED}
 * @param requestId      the admin-request id (== {@code eventId})
 * @param actingUserId   the PLAYER user id requesting the registration (captain)
 * @param actingRole     the role of the acting user (PLAYER)
 * @param buildingId     the building where the player is connected
 * @param tournamentId   the target tournament id
 * @param teamName       the team name (nullable for individual registration)
 * @param teamMemberIds  the team member ids (nullable for individual registration;
 *                       when non-null it may be empty for a single-player team)
 * @param createdAt      the request creation instant (never {@code null})
 *
 * @see com.gameplatform.shared.dto.TournamentParticipantsUpsertedEventDto
 */
public record ParticipantRegisterRequestedEventDto(
        String eventId,
        String eventType,
        String requestId,
        String actingUserId,
        String actingRole,
        String buildingId,
        String tournamentId,
        String teamName,
        List<String> teamMemberIds,
        Instant createdAt
) {
}