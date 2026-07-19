package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la gestione degli utenti nel sistema locale.
 * <p>
 * Fornisce operazioni CRUD per gli utenti, includendo sia gli utenti
 * registrati localmente sia la replica degli utenti provenienti dal
 * sistema centrale. Supporta la ricerca per identificativo e username.
 * </p>
 *
 * @see User
 */
public interface UserRepository {
    /**
     * Salva o aggiorna un utente.
     *
     * @param user l'utente da persistere
     * @return l'utente persistito
     */
    User save(User user);

    /**
     * Cerca un utente in base al suo identificativo.
     *
     * @param userId l'identificativo dell'utente
     * @return un {@code Optional} contenente l'utente, vuoto se non trovato
     */
    Optional<User> findById(UserId userId);

    /**
     * Cerca un utente in base al nome utente.
     *
     * @param username il nome utente da ricercare
     * @return un {@code Optional} contenente l'utente, vuoto se non trovato
     */
    Optional<User> findByUsername(String username);

    /**
     * Salva o aggiorna una lista di utenti in un'unica operazione bulk.
     *
     * @param users la lista degli utenti da persistere
     */
    void saveAll(List<User> users);

    /**
     * Restituisce tutti gli utenti presenti esclusivamente nella tabella
     * {@code replicated_users} (la tabella {@code users} delle registrazioni
     * locali &egrave; esclusa intenzionalmente).
     *
     * @return la lista degli utenti replicati
     */
    List<User> findAllReplicated();

    /**
     * Restituisce il numero di utenti presenti nella tabella
     * {@code replicated_users}.
     *
     * @return il conteggio degli utenti replicati
     */
    long count();
}