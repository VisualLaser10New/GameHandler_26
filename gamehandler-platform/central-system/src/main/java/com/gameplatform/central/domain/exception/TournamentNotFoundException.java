package com.gameplatform.central.domain.exception;

import com.gameplatform.shared.domain.model.TournamentId;

/**
 * Eccezione lanciata quando un torneo identificato dal proprio
 * {@link TournamentId} non puo' essere risolto (GET /{id}, oppure
 * registrazione/annullamento registrazione su un torneo assente). Mappata a
 * HTTP 404.
 *
 * @see TournamentId
 */
public class TournamentNotFoundException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public TournamentNotFoundException(String message) {
        super(message);
    }
}
