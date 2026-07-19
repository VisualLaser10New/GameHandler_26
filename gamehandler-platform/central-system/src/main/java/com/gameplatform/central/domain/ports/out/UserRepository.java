package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per gli utenti del sistema.
 *
 * <p>Espone le operazioni di salvataggio e ricerca degli utenti per
 * identificativo, nome utente ed email, oltre all'elenco completo, a supporto
 * delle funzionalità di autenticazione e gestione degli account.</p>
 *
 * @see User
 * @see UserId
 */
public interface UserRepository {

    /**
     * Salva o aggiorna l'utente fornito.
     *
     * @param user l'utente da persistere; non deve essere {@code null}
     * @return l'utente salvato, eventualmente arricchito di metadati di persistenza
     * @throws IllegalArgumentException se {@code user} è {@code null}
     */
    User save(User user);

    /**
     * Restituisce l'utente identificato dall'id indicato.
     *
     * @param id l'identificativo dell'utente; non deve essere {@code null}
     * @return un {@link Optional} contenente l'utente trovato, o vuoto se assente
     * @throws IllegalArgumentException se {@code id} è {@code null}
     */
    Optional<User> findById(UserId id);

    /**
     * Restituisce l'utente avente il nome utente indicato.
     *
     * @param username il nome utente; non deve essere {@code null}
     * @return un {@link Optional} contenente l'utente trovato, o vuoto se assente
     * @throws IllegalArgumentException se {@code username} è {@code null}
     */
    Optional<User> findByUsername(String username);

    /**
     * Restituisce l'utente avente l'email indicata.
     *
     * @param email l'indirizzo email; non deve essere {@code null}
     * @return un {@link Optional} contenente l'utente trovato, o vuoto se assente
     * @throws IllegalArgumentException se {@code email} è {@code null}
     */
    Optional<User> findByEmail(String email);

    /**
     * Restituisce tutti gli utenti persistiti.
     *
     * @return la lista degli utenti; mai {@code null}, eventualmente vuota
     */
    List<User> findAll();
}
