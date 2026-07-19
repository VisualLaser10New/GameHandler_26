package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.domain.model.TournamentId;

/**
 * Out-port for the {@code team_members_local} read-only replica (BUG-TEAM-3).
 * {@code save} is an idempotent upsert by the composite PK
 * ({@code tournamentId}, {@code teamId}, {@code userId}); the sync service
 * physically removes a tournament's full team→user membership snapshot via
 * {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency) before re-inserting the fresh snapshot.
 */
public interface TeamMembersLocalRepository {

    /**
     * Salva (o aggiorna) l'appartenenza di un utente a un team all'interno
     * di un torneo. Operazione idempotente basata sulla chiave composita
     * (tournamentId, teamId, userId).
     *
     * @param tournamentId l'identificativo del torneo
     * @param teamId       l'identificativo del team
     * @param userId       l'identificativo dell'utente
     */
    void save(String tournamentId, String teamId, String userId);

    /**
     * Elimina tutte le appartenenze ai team per un determinato torneo.
     * Utilizzato dal servizio di sincronizzazione per la sostituzione
     * completa dello snapshot (full-snapshot replace idempotency).
     *
     * @param tournamentId l'identificativo del torneo
     */
    void deleteByTournament(TournamentId tournamentId);
}