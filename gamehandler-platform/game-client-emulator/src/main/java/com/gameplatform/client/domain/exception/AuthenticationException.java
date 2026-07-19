package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Eccezione sollevata da {@link ApiClient} quando il server locale risponde
 * con uno stato {@code 401 Unauthorized}, indicando che il token di
 * autenticazione è mancante, scaduto o non valido.
 * <p>
 * Il livello UI è tenuto a reindirizzare l'utente verso la {@code LoginView}
 * e a cancellare lo stato della sessione.
 */
public class AuthenticationException extends RuntimeException {

    /**
     * Costruisce una {@code AuthenticationException} con il messaggio di
     * dettaglio specificato.
     *
     * @param message il messaggio di dettaglio (può essere {@code null}).
     */
    public AuthenticationException(String message) {
        super(message);
    }
}
