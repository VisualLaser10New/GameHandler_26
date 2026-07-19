package com.gameplatform.central.domain.ports.in;

import com.gameplatform.central.domain.model.User;

/**
 * Caso d'uso che registra un nuovo utente nel sistema centrale a partire
 * dai dati anagrafici e di accesso forniti.
 */
public interface RegisterUserUseCase {

    /**
     * Registra un nuovo utente con le credenziali e l'email indicate.
     *
     * @param username il nome utente scelto per il nuovo account; non deve essere {@code null} né vuoto
     * @param password la password in chiaro per il nuovo account; non deve essere {@code null} né vuota
     * @param email l'indirizzo email associato all'utente; non deve essere {@code null} né vuoto
     * @return l'entità {@link User} rappresentante l'utente appena registrato
     * @throws com.gameplatform.shared.domain.exception.DuplicateUserException se esiste già un utente con lo stesso nome utente o email
     * @throws IllegalArgumentException se uno dei parametri è {@code null} o vuoto
     */
    User register(String username, String password, String email);
}

