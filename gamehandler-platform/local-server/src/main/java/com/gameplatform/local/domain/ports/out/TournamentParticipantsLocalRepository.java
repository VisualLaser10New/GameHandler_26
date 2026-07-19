package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.shared.domain.model.TournamentId;

import java.util.List;

/**
 * Out-port for the {@code tournament_participants_local} read-only
 * replica (PIANO §7.B). {@code save} is an idempotent upsert by the
 * composite PK ({@code tournamentId}, {@code participantId}); the sync
 * service physically removes a tournament's full participant snapshot
 * via {@link #deleteByTournament(TournamentId)} (full-snapshot replace
 * idempotency). {@link #deleteByTournamentAndParticipantId} targets an
 * individual registration row.
 */
public interface TournamentParticipantsLocalRepository {

    /**
     * Salva o aggiorna un partecipante al torneo. Operazione idempotente
     * basata sulla chiave composita (tournamentId, participantId).
     *
     * @param participant il partecipante da persistere
     * @return il partecipante persistito
     */
    TournamentParticipantLocal save(TournamentParticipantLocal participant);

    /**
     * Restituisce tutti i partecipanti di un determinato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return la lista dei partecipanti al torneo specificato
     */
    List<TournamentParticipantLocal> findByTournament(TournamentId tournamentId);

    /**
     * Elimina tutti i partecipanti di un determinato torneo. Utilizzato
     * per la sostituzione completa dello snapshot dei partecipanti.
     *
     * @param tournamentId l'identificativo del torneo
     */
    void deleteByTournament(TournamentId tournamentId);

    /**
     * Elimina un singolo partecipante da un torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @param participantId l'identificativo del partecipante da rimuovere
     */
    void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId);
}