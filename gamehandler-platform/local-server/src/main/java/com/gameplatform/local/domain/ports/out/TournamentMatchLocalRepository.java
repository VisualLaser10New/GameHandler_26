package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code tournament_matches_local} read-only replica.
 * {@code save} is an idempotent upsert by PK {@code id} (mirror of
 * {@link GameDefinitionLocalRepository#save}).
 */
public interface TournamentMatchLocalRepository {
    /**
     * Salva o aggiorna un incontro del torneo. Operazione idempotente
     * basata sulla chiave primaria {@code id}.
     *
     * @param match l'incontro del torneo da persistere
     * @return l'incontro del torneo persistito
     */
    TournamentMatchLocal save(TournamentMatchLocal match);

    /**
     * Cerca un incontro del torneo in base al suo identificativo.
     *
     * @param id l'identificativo dell'incontro
     * @return un {@code Optional} contenente l'incontro, vuoto se non trovato
     */
    Optional<TournamentMatchLocal> findById(TournamentMatchId id);

    /**
     * Restituisce tutti gli incontri di un determinato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return la lista degli incontri del torneo specificato
     */
    List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId);

    /**
     * Restituisce tutti gli incontri programmati a cui partecipa un determinato
     * utente.
     *
     * @param userId l'identificativo dell'utente partecipante
     * @return la lista degli incontri programmati per l'utente specificato
     */
    List<TournamentMatchLocal> findScheduledByParticipant(String userId);

    /**
     * Elimina un incontro del torneo in base al suo identificativo.
     *
     * @param id l'identificativo dell'incontro da eliminare
     */
    void deleteById(TournamentMatchId id);
}