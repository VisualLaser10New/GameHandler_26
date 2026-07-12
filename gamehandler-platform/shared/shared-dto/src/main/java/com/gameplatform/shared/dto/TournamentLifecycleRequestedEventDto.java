package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Parametric outbox payload for the three lifecycle admin-request events
 * {@code TOURNAMENT_OPEN_REQUESTED}, {@code TOURNAMENT_CANCEL_REQUESTED} and
 * {@code TOURNAMENT_SCHEDULE_REQUESTED} (PIANO §7.B W12), all carrying the same
 * {@code (tournamentId)} payload and discriminated only by {@code eventType}.
 * Consumed by the Central {@code SyncEventProcessor} §7.A.7 branches which
 * dispatch to {@code OpenTournamentRegistrationUseCase.open},
 * {@code CancelTournamentUseCase.cancel} or
 * {@code ScheduleTournamentMatchesUseCase.schedule} respectively.
 *
 * <p>The {@code requestId} equals the Local outbox {@code eventId}; the Central
 * return event (e.g. {@code TOURNAMENT_SUMMARY_UPSERTED}) carries it back as
 * {@code originatingRequestId} so the Local can {@code markCompleted}.</p>
 *
 * @param eventId        the Local outbox event id (UUID)
 * @param eventType      one of {@code TOURNAMENT_OPEN_REQUESTED},
 *                       {@code TOURNAMENT_CANCEL_REQUESTED},
 *                       {@code TOURNAMENT_SCHEDULE_REQUESTED}
 * @param requestId      the admin-request id (== {@code eventId})
 * @param actingUserId   the admin user id (PLATFORM_ADMIN) requesting the change
 * @param actingRole     the role of the acting admin
 * @param buildingId     the building where the admin is connected
 * @param tournamentId   the target tournament id
 * @param createdAt      the request creation instant
 */
public record TournamentLifecycleRequestedEventDto(
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