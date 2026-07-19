package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per le partite di torneo.
 *
 * <p>Espone operazioni di salvataggio, ricerca e cancellazione delle partite,
 * incluse varianti con lock ottimistico/pessimistico per gli aggiornamenti
 * concorrenti all'interno di una transazione.</p>
 *
 * @see TournamentMatch
 * @see TournamentMatchId
 * @see TournamentId
 */
public interface TournamentMatchRepository {

    /**
     * Salva o aggiorna la partita di torneo fornita.
     *
     * @param match la partita da persistere; non deve essere {@code null}
     * @return la partita salvata, eventualmente arricchita di metadati di persistenza
     * @throws IllegalArgumentException se {@code match} è {@code null}
     */
    TournamentMatch save(TournamentMatch match);

    /**
     * Restituisce la partita identificata dall'id indicato.
     *
     * @param id l'identificativo della partita; non deve essere {@code null}
     * @return un {@link Optional} contenente la partita trovata, o vuoto se assente
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    Optional<TournamentMatch> findById(TournamentMatchId id);

    /**
     * Restituisce tutte le partite del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo; non deve essere {@code null}
     * @return la lista delle partite del torneo; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null}
     */
    List<TournamentMatch> findByTournament(TournamentId tournamentId);

    /**
     * Elimina la partita identificata dall'id indicato, se presente.
     *
     * <p>Se l'id non corrisponde ad alcuna partita, l'operazione non ha effetto.</p>
     *
     * @param id l'identificativo della partita da eliminare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    void deleteById(TournamentMatchId id);

    /**
     * Restituisce la partita identificata dall'id acquisendo un lock per
     * aggiornamento, all'interno della transazione attiva.
     *
     * @param id l'identificativo della partita; non deve essere {@code null}
     * @return un {@link Optional} contenente la partita trovata e bloccata, o vuoto se assente
     * @throws IllegalArgumentException se {@code id} è {@code null}
     * @throws IllegalStateException    se non è attiva alcuna transazione
     */
    Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id);

    /**
     * Restituisce la partita del torneo, round e posizione nel bracket indicati,
     * acquisendo un lock per aggiornamento all'interno della transazione attiva.
     *
     * @param tournamentId    l'identificativo del torneo; non deve essere {@code null}
     * @param round           il numero del round; non negativo
     * @param bracketPosition la posizione nel bracket; non negativa
     * @return un {@link Optional} contenente la partita trovata e bloccata, o vuoto se assente
     * @throws IllegalArgumentException se {@code tournamentId} è {@code null} o se i parametri numerici sono negativi
     * @throws IllegalStateException    se non è attiva alcuna transazione
     */
    Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition);
}
