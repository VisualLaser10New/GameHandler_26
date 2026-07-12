package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code TOURNAMENT_PARTICIPANTS_UPSERTED} event in the
 * Central→Local replication flow (PIANO §7.A.3). Carries a full snapshot of the
 * per-tournament participants so the local node can replace its projection
 * idempotently (delete+insert by {@code tournamentId}).
 *
 * <p>{@code originatingRequestId} is nullable: {@code null} for events raised on
 * the FASE 4/5/6 path (register/unregister) and non-null for the
 * SyncEventProcessor path §7.A.3 (admin-request closure).</p>
 *
 * @param eventId              outbox event id (UUID)
 * @param eventType            always {@code TOURNAMENT_PARTICIPANTS_UPSERTED}
 * @param tournamentId         the tournament id
 * @param participants         the full participant snapshot
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param updatedAt            last mutation instant
 */
public record TournamentParticipantsEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        List<TournamentParticipantViewDto> participants,
        String originatingRequestId,
        Instant updatedAt
) {
    public TournamentParticipantsEventDto(String eventId, String eventType, String tournamentId,
                                          List<TournamentParticipantViewDto> participants, Instant updatedAt) {
        this(eventId, eventType, tournamentId, participants, null, updatedAt);
    }
}