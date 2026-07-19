package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code tournaments_summary_local} read-only replica (PIANO
 * §7.B). Mirror of {@link TournamentMatchLocalRepository} and
 * {@link GameDefinitionLocalRepository}. {@code save} is an idempotent upsert
 * by PK {@code tournamentId}; the projection row is physically removed on a
 * tombstone ({@code deleted=true}) via {@link #deleteById(TournamentId)}.
 */
public interface TournamentSummaryLocalRepository {

    /**
     * Salva o aggiorna il riepilogo di un torneo. Operazione idempotente
     * basata sulla chiave primaria {@code tournamentId}.
     *
     * @param summary il riepilogo del torneo da persistere
     * @return il riepilogo del torneo persistito
     */
    TournamentSummaryLocal save(TournamentSummaryLocal summary);

    /**
     * Cerca il riepilogo di un torneo in base al suo identificativo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return un {@code Optional} contenente il riepilogo, vuoto se non trovato
     */
    Optional<TournamentSummaryLocal> findById(TournamentId tournamentId);

    /**
     * Restituisce tutti i riepiloghi dei tornei presenti nel sistema locale.
     *
     * @return la lista completa dei riepiloghi dei tornei
     */
    List<TournamentSummaryLocal> findAll();

    /**
     * Elimina il riepilogo di un torneo dal sistema locale.
     *
     * @param tournamentId l'identificativo del torneo da eliminare
     */
    void deleteById(TournamentId tournamentId);

    /**
     * Verifica se esiste il riepilogo per un determinato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return {@code true} se il riepilogo esiste, {@code false} altrimenti
     */
    boolean existsById(TournamentId tournamentId);
}
