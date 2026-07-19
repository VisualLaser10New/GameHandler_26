package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando viene rilevato un evento duplicato all'interno del sistema.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare tentativi di
 * inserimento o elaborazione di un evento gia' presente, violando il vincolo di
 * univocita'.</p>
 *
 * @see RuntimeException
 */
public class DuplicateEventException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public DuplicateEventException(String message) {
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
    public DuplicateEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
