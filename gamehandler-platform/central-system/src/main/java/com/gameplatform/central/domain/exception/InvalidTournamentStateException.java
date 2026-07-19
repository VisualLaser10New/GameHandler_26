package com.gameplatform.central.domain.exception;

import com.gameplatform.shared.domain.model.TournamentStatus;

/**
 * Eccezione lanciata dai metodi di transizione di
 * {@link com.gameplatform.central.domain.model.Tournament}
 * ({@code openRegistration}, {@code cancel}, {@code startProgress},
 * {@code complete}) quando lo {@link TournamentStatus} corrente non ammette la
 * transizione richiesta. Mappata a HTTP 400.
 *
 * @see com.gameplatform.central.domain.model.Tournament
 * @see TournamentStatus
 */
public class InvalidTournamentStateException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public InvalidTournamentStateException(String message) {
        super(message);
    }
}
