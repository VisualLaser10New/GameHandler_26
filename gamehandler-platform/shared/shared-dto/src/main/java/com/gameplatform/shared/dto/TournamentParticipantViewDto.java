package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * Read-model view of a single tournament participant (individual or team) for
 * the {@code TOURNAMENT_PARTICIPANTS_UPSERTED} Central→Local replication flow
 * (PIANO §7.A.3 / §7.B). Unlike {@link TournamentParticipantDto} (which carries
 * only the identity/display snapshot used by the Central REST endpoint), this
 * view also carries the registration instant so the local projection can
 * preserve the registration ordering used by the bracket builder.
 *
 * @param participantId the participant id (a {@code UserId} value when
 *                      {@code isTeam == false}, a {@code TeamId} value otherwise)
 * @param isTeam        whether the participant is a team
 * @param displayName   the display name (username for individuals, team name for teams)
 * @param registeredAt  the registration instant
 */
public record TournamentParticipantViewDto(
        String participantId,
        boolean isTeam,
        String displayName,
        Instant registeredAt
) {
}