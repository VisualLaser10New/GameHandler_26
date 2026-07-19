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

    /**
     * Elimina in modo definitivo un torneo ancora in stato {@code DRAFT}.
     *
     * @param tournamentId l'identificativo del torneo da eliminare; non deve essere {@code null}
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se il torneo non è in stato {@code DRAFT}
     */
    void delete(TournamentId tournamentId, String originatingRequestId);
}