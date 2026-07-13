package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code TEAM_MEMBERS_UPSERTED} event in the
 * Central→Local replication flow (BUG-TEAM-3). Carries a full snapshot of
 * the per-tournament team→user membership so the local node can replace its
 * {@code team_members_local} projection idempotently (delete+insert by
 * {@code tournamentId}).
 *
 * <p>Structural twin of {@link TournamentParticipantsEventDto}. The
 * {@code originatingRequestId} is {@code null} on the producer path
 * ({@code TournamentRegistrationService.registerTeam} /
 * {@code unregister}); the admin-request closure for the registration use
 * case is carried out by the parallel {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * return event (see {@code TournamentParticipantsLocalSyncService.markCompletedIfRequested}),
 * so this event never drives an {@code admin_requests_local} state transition
 * on the Local side.
 *
 * @param eventId              outbox event id (UUID)
 * @param eventType            always {@code TEAM_MEMBERS_UPSERTED}
 * @param tournamentId         the tournament id
 * @param teams               the full per-tournament team→user membership snapshot
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param updatedAt            last mutation instant
 */
public record TeamMembersEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TeamMemberEntryDto> teams,
        String originatingRequestId,
        Instant updatedAt
) {
    public TeamMembersEventDto(String eventId, String eventType, String tournamentId,
                               List<TeamMemberEntryDto> teams, Instant updatedAt) {
        this(eventId, eventType, tournamentId, teams, null, updatedAt);
    }
}