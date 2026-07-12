package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code TOURNAMENT_STANDINGS_UPSERTED} event in the
 * Central→Local replication flow (PIANO §7.A.3). Carries a full snapshot of the
 * per-tournament standings so the local node can replace its projection
 * idempotently (delete+insert by {@code tournamentId}).
 *
 * <p>{@code originatingRequestId} is nullable: {@code null} for events raised on
 * the FASE 5/6 path (standings recomputed after a match completion) and non-null
 * for the SyncEventProcessor path §7.A.3, where it carries the id of the
 * originating outbox event for idempotency tracking / admin-request closure.</p>
 *
 * @param eventId              outbox event id (UUID)
 * @param eventType            always {@code TOURNAMENT_STANDINGS_UPSERTED}
 * @param tournamentId         the tournament id
 * @param entries             the full standings snapshot
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param updatedAt            last mutation instant
 */
public record TournamentStandingsEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TournamentStandingDto> entries,
        String originatingRequestId,
        Instant updatedAt
) {
    public TournamentStandingsEventDto(String eventId, String eventType, String tournamentId,
                                      List<TournamentStandingDto> entries, Instant updatedAt) {
        this(eventId, eventType, tournamentId, entries, null, updatedAt);
    }
}