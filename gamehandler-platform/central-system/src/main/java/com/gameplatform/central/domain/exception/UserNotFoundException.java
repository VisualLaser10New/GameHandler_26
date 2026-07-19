package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando l'utente richiesto non puo' essere individuato nel
 * sistema.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare l'assenza di
 * un utente corrispondente a un identificativo fornito.</p>
 *
 * @see RuntimeException
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public UserNotFoundException(String message) {
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
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
