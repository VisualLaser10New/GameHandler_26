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

    /**
     * Emette un evento outbox di riepilogo del torneo con lo snapshot corrente.
     *
     * @param tournamentId l'identificativo del torneo di cui emettere il riepilogo; non deve essere {@code null}
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata diretta
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @see #emitSummary(TournamentId)
     */
    void emitSummary(TournamentId tournamentId, String originatingRequestId);

    /**
     * Emette un evento outbox di riepilogo del torneo senza identificativo di origine.
     *
     * @param tournamentId l'identificativo del torneo di cui emettere il riepilogo; non deve essere {@code null}
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @see #emitSummary(TournamentId, String)
     */
    default void emitSummary(TournamentId tournamentId) {
        emitSummary(tournamentId, null);
    }
}