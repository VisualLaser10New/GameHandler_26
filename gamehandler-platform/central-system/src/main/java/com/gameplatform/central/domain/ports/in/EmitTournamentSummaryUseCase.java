package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;

/**
 * Emits a {@code TOURNAMENT_SUMMARY_UPSERTED} outbox event carrying the current
 * snapshot of a tournament (status, buildings, participantsCount) with the
 * supplied {@code originatingRequestId}. Used as the special-ack return event
 * for admin-request flows that do not naturally produce a summary upsert on
 * their own (e.g. {@code TOURNAMENT_SCHEDULE_REQUESTED}: the schedule use case
 * emits {@code TOURNAMENT_MATCH_SCHEDULED} rows which do NOT carry the
 * originatingRequestId, so the Local {@code admin_requests_local} row would
 * stay PENDING; this use case closes that gap by emitting a single
 * {@code TOURNAMENT_SUMMARY_UPSERTED} that the Local
 * {@code TournamentSummarySyncService.markCompletedIfRequested} treats as the
 * completion signal).
 *
 * <p>No-op (logs a warning) when the outbox deps are {@code null} (legacy test
 * ctor of {@code TournamentService}).</p>
 */
public interface EmitTournamentSummaryUseCase {
    void emitSummary(TournamentId tournamentId, String originatingRequestId);

    default void emitSummary(TournamentId tournamentId) {
        emitSummary(tournamentId, null);
    }
}