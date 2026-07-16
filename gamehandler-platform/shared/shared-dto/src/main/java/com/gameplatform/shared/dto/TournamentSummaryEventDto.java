package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.time.Instant;
import java.util.List;

/**
 * Outbox payload for the {@code TOURNAMENT_SUMMARY_UPSERTED} event in the
 * Central→Local replication flow (use case §7.A.1). Carries a flattened
 * tournament summary so the local node can upsert its projection.
 *
 * <p>{@code deleted == true} marks a tombstone: the local must delete its
 * projection for the given {@code tournamentId} rather than upserting.
 * {@code originatingRequestId} is nullable: {@code null} for events raised by
 * the direct REST branch and non-null for the SyncEventProcessor branch §7.A.3,
 * where it carries the id of the originating outbox event for idempotency
 * tracking.</p>
 *
 * <p>{@code errorMessage} is non-null when the Central use case that handled a
 * {@code *_REQUESTED} event rejected the request (e.g. a tournament-lifecycle
 * transition refused because the current {@code TournamentStatus} does not admit
 * it, or the tournament was not found). In that case the local
 * {@code TournamentSummarySyncService} closes the matching
 * {@code admin_requests_local} row as {@code FAILED} with the readable reason
 * in {@code result_data}, instead of {@code COMPLETED} — closing the loop
 * immediately rather than letting the
 * {@code admin.request.timeout-ms} deadline fire 30 minutes later and surface
 * as a vague "TIMEOUT" to the platform admin (BUG-CANCEL-PENDING).</p>
 *
 * @param eventId             outbox event id (UUID)
 * @param eventType           always {@code TOURNAMENT_SUMMARY_UPSERTED}
 * @param tournamentId        the tournament id
 * @param name                tournament name
 * @param gameType            the game type
 * @param teamBased           whether the tournament is team-based
 * @param teamSize            team size (1 for individual)
 * @param status              the tournament status
 * @param startsAt            scheduled start instant
 * @param endsAt              actual end instant (null while not COMPLETED)
 * @param buildingIds         the buildings hosting the tournament
 * @param participantsCount   number of enrolled participants
 * @param updatedAt           last mutation instant
 * @param deleted             {@code true} for a tombstone event
 * @param originatingRequestId id of the originating request/event (nullable)
 * @param errorMessage        readable rejection reason (nullable; non-null ⇒
 *                            the local must mark the matching admin-request
 *                            {@code FAILED} instead of {@code COMPLETED})
 */
public record TournamentSummaryEventDto(
        String eventId,
        String eventType,
        String tournamentId,
        String name,
        GameType gameType,
        boolean teamBased,
        int teamSize,
        TournamentStatus status,
        Instant startsAt,
        Instant endsAt,
        List<String> buildingIds,
        int participantsCount,
        Instant updatedAt,
        boolean deleted,
        String originatingRequestId,
        String errorMessage
) {
    /**
     * Backward-compat secondary ctor (no failure indication): delegates to the
     * canonical 16-arg ctor with {@code errorMessage = null}. Existing call
     * sites (FASE 7.A create/open/update/delete flow, admin-request success
     * branches and tests) keep the previous 15-arg arity undisturbed.
     */
    public TournamentSummaryEventDto(String eventId, String eventType, String tournamentId, String name,
                                     GameType gameType, boolean teamBased, int teamSize, TournamentStatus status,
                                     Instant startsAt, Instant endsAt, List<String> buildingIds,
                                     int participantsCount, Instant updatedAt, boolean deleted,
                                     String originatingRequestId) {
        this(eventId, eventType, tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt,
                buildingIds, participantsCount, updatedAt, deleted, originatingRequestId, null);
    }

    /**
     * Backward-compat secondary ctor (legacy 14-arg, no tombstone/originatingRequestId/errorMessage).
     */
    public TournamentSummaryEventDto(String eventId, String eventType, String tournamentId, String name,
                                     GameType gameType, boolean teamBased, int teamSize, TournamentStatus status,
                                     Instant startsAt, Instant endsAt, List<String> buildingIds,
                                     int participantsCount, Instant updatedAt) {
        this(eventId, eventType, tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt,
                buildingIds, participantsCount, updatedAt, false, null, null);
    }
}