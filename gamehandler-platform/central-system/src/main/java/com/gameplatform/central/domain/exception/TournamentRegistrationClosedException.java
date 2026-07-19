package com.gameplatform.central.domain.exception;

import com.gameplatform.shared.domain.model.TournamentStatus;

/**
 * Eccezione lanciata quando un comando di registrazione o annullamento
 * registrazione viene emesso su un torneo il cui {@link TournamentStatus} non e'
 * {@code OPEN_REGISTRATION}. Mappata a HTTP 409.
 *
 * @see TournamentStatus
 */
public class TournamentRegistrationClosedException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public TournamentRegistrationClosedException(String message) {
        super(message);
    }
}
