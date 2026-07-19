package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.LocalSignupUser;

/**
 * Repository out-port per la registrazione locale degli utenti (signup).
 * <p>
 * Gestisce la persistenza degli utenti registrati direttamente tramite il
 * server locale, prima che vengano sincronizzati con il sistema centrale.
 * Fornisce metodi per verificare l'univocit&agrave; di username ed email.
 * </p>
 *
 * @see LocalSignupUser
 */
public interface LocalSignupUserRepository {
    /**
     * Verifica se esiste gi&agrave; un utente con lo username specificato.
     *
     * @param username lo username da verificare
     * @return {@code true} se lo username &egrave; gi&agrave; in uso, {@code false} altrimenti
     */
    boolean existsByUsername(String username);

    /**
     * Verifica se esiste gi&agrave; un utente con l'email specificata.
     *
     * @param email l'email da verificare
     * @return {@code true} se l'email &egrave; gi&agrave; in uso, {@code false} altrimenti
     */
    boolean existsByEmail(String email);

    /**
     * Salva un nuovo utente registrato localmente.
     *
     * @param user l'utente registrato da persistere
     * @return l'utente persistito
     */
    LocalSignupUser save(LocalSignupUser user);
}
