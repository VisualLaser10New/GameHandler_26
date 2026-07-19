package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentDto;
import java.time.Instant;
import java.util.List;

/**
 * Use case §7.A.1: updates the mutable fields ({@code name}, {@code startsAt},
 * {@code buildingIds}) of a tournament still in {@code DRAFT} status. The
 * domain guard rejects updates once the tournament has left {@code DRAFT}.
 *
 * <p>{@code originatingRequestId} is nullable: {@code null} for the direct REST
 * branch (PUT /api/tournaments/{id}) and non-null for the SyncEventProcessor
 * branch §7.A.3, where it carries the id of the originating outbox event for
 * idempotency tracking.</p>
 */
public interface UpdateTournamentUseCase {

    /**
     * Aggiorna i campi modificabili di un torneo ancora in stato {@code DRAFT}.
     *
     * @param tournamentId l'identificativo del torneo da aggiornare; non deve essere {@code null}
     * @param name il nuovo nome del torneo; non deve essere {@code null} né vuoto
     * @param startsAt la nuova data di inizio del torneo; può essere {@code null} per lasciarla non definita
     * @param buildingIds la lista degli identificativi delle strutture associate; non deve essere {@code null}; se vuota il torneo non è associato ad alcuna struttura
     * @param originatingRequestId l'identificativo della richiesta origine per la tracciabilità; può essere {@code null} nel caso di chiamata REST diretta
     * @return il {@link TournamentDto} rappresentante il torneo aggiornato
     * @throws com.gameplatform.shared.domain.exception.TournamentNotFoundException se il torneo non esiste
     * @throws com.gameplatform.shared.domain.exception.InvalidTournamentStateException se il torneo non è in stato {@code DRAFT}
     */
    TournamentDto update(TournamentId tournamentId, String name, Instant startsAt,
                         List<String> buildingIds, String originatingRequestId);
}