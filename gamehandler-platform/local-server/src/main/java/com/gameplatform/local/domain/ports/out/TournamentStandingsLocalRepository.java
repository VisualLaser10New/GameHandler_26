package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;

/**
 * Out-port for the {@code tournament_standings_local} read-only replica
 * (PIANO §7.B). {@code save} is an idempotent upsert by the composite
 * PK ({@code tournamentId}, {@code participantId}); the sync service
 * physically removes a tournament's full standings snapshot via
 * {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency).
 */
public interface TournamentStandingsLocalRepository {

    /**
     * Salva o aggiorna una posizione in classifica del torneo. Operazione
     * idempotente basata sulla chiave composita (tournamentId, participantId).
     *
     * @param standing la posizione in classifica da persistere
     * @return la posizione in classifica persistita
     */
    TournamentStandingLocal save(TournamentStandingLocal standing);

    /**
     * Restituisce tutte le posizioni in classifica di un determinato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return la lista delle posizioni in classifica del torneo specificato
     */
    List<TournamentStandingLocal> findByTournament(TournamentId tournamentId);

    /**
     * Elimina tutte le posizioni in classifica di un determinato torneo.
     * Utilizzato per la sostituzione completa dello snapshot della classifica.
     *
     * @param tournamentId l'identificativo del torneo
     */
    void deleteByTournament(TournamentId tournamentId);

    /**
     * Verifica se esiste una posizione in classifica per un determinato
     * partecipante in un torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @param participantId l'identificativo del partecipante
     * @return {@code true} se la posizione esiste, {@code false} altrimenti
     */
    boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}