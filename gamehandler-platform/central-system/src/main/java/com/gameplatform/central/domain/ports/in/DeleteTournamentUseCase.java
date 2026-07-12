package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;

/**
 * Use case §7.A.1: hard-deletes a tournament that is still in {@code DRAFT}
 * status and emits a {@code TOURNAMENT_SUMMARY_UPSERTED} tombstone event with
 * {@code deleted = true} for Central→Local replication.
 *
 * <p>{@code originatingRequestId} is nullable: {@code null} for the direct REST
 * branch (DELETE /api/tournaments/{id}) and non-null for the SyncEventProcessor
 * branch §7.A.3.</p>
 */
public interface DeleteTournamentUseCase {
    void delete(TournamentId tournamentId, String originatingRequestId);
}