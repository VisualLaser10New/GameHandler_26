package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per i tornei.
 *
 * <p>Espone operazioni di salvataggio, ricerca per identificativo e per stato,
 * nonché varianti con lock per aggiornamenti concorrenti all'interno di una
 * transazione.</p>
 *
 * @see Tournament
 * @see TournamentId
 * @see TournamentStatus
 */
public interface TournamentRepository {

    /**
     * Salva o aggiorna il torneo fornito.
     *
     * @param tournament il torneo da persistere; non deve essere {@code null}
     * @return il torneo salvato, eventualmente arricchito di metadati di persistenza
     * @throws IllegalArgumentException se {@code tournament} è {@code null}
     */
    Tournament save(Tournament tournament);

    /**
     * Restituisce il torneo identificato dall'id indicato.
     *
     * @param id l'identificativo del torneo; non deve essere {@code null}
     * @return un {@link Optional} contenente il torneo trovato, o vuoto se assente
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    Optional<Tournament> findById(TournamentId id);

    /**
     * Restituisce tutti i tornei persistiti.
     *
     * @return la lista dei tornei; mai {@code null}, eventualmente vuota
     */
    List<Tournament> findAll();

    /**
     * Restituisce i tornei aventi lo stato indicato.
     *
     * @param status lo stato dei tornei da cercare; non deve essere {@code null}
     * @return la lista dei tornei nello stato indicato; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code status} è {@code null}
     */
    List<Tournament> findByStatus(TournamentStatus status);

    /**
     * Verifica l'esistenza di un torneo con l'id indicato.
     *
     * @param id l'identificativo del torneo; non deve essere {@code null}
     * @return {@code true} se il torneo esiste, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    boolean existsById(TournamentId id);

    /**
     * Elimina il torneo identificato dall'id indicato, se presente.
     *
     * <p>Se l'id non corrisponde ad alcun torneo, l'operazione non ha effetto.</p>
     *
     * @param id l'identificativo del torneo da eliminare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    void deleteById(TournamentId id);

    /**
     * Restituisce il torneo identificato dall'id acquisendo un lock per
     * aggiornamento, all'interno della transazione attiva.
     *
     * @param id l'identificativo del torneo; non deve essere {@code null}
     * @return un {@link Optional} contenente il torneo trovato e bloccato, o vuoto se assente
     * @throws IllegalArgumentException se {@code id} è {@code null}
     * @throws IllegalStateException    se non è attiva alcuna transazione
     */
    Optional<Tournament> findByIdForUpdate(TournamentId id);
}
