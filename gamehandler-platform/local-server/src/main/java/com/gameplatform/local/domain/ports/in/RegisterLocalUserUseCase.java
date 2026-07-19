package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.LocalSignupUser;

/**
 * Use case per la registrazione di un nuovo utente locale. Crea un account
 * utente con le credenziali fornite, effettuando le validazioni necessarie
 * su username, password ed email.
 *
 * @see com.gameplatform.local.domain.model.LocalSignupUser
 */
public interface RegisterLocalUserUseCase {
    /**
     * Registra un nuovo utente locale con le credenziali specificate.
     *
     * @param username nome utente scelto
     * @param password password in chiaro per l'account
     * @param email    indirizzo email dell'utente
     * @return l'utente locale registrato
     */
    LocalSignupUser register(String username, String password, String email);
}
