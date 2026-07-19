package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando le credenziali fornite per l'autenticazione non sono
 * valide.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare un tentativo
 * di accesso con credenziali errate o non risolvibili.</p>
 *
 * @see RuntimeException
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio e la causa specificati.
     *
     * @param message descrizione dell'errore; puo' essere {@code null}
     * @param cause   causa originaria dell'errore, recuperabile tramite
     *                {@link #getCause()}; puo' essere {@code null}
     * @see RuntimeException#RuntimeException(String, Throwable)
     */
    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
