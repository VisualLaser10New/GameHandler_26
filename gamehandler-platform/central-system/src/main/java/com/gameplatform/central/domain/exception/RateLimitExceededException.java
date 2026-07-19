package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando un client ha superato il numero di richieste consentite
 * all'interno di una finestra temporale.
 *
 * <p>Mappata a HTTP 429 Too Many Requests.</p>
 *
 * @see RuntimeException
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public RateLimitExceededException(String message) {
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
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
