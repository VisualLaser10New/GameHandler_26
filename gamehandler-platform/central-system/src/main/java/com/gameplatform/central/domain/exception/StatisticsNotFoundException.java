package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando le statistiche richieste non possono essere trovate
 * nel sistema.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare l'assenza di
 * statistiche corrispondenti a un identificativo o a un contesto forniti.</p>
 *
 * @see RuntimeException
 */
public class StatisticsNotFoundException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public StatisticsNotFoundException(String message) {
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
    public StatisticsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
