package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Eccezione sollevata da {@link ApiClient} quando il server locale risponde
 * con uno stato {@code 403 Forbidden}, indicando che il principale
 * autenticato non possiede il ruolo richiesto dall'endpoint.
 * <p>
 * Il livello UI mostra un banner non bloccante che spiega il permesso
 * mancante; la navigazione corrente viene preservata.
 */
public class AuthorizationException extends RuntimeException {

    /**
     * Costruisce una {@code AuthorizationException} con il messaggio di
     * dettaglio specificato.
     *
     * @param message il messaggio di dettaglio (può essere {@code null}).
     */
    public AuthorizationException(String message) {
        super(message);
    }
}
