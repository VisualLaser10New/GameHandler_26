package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando una definizione di gioco non soddisfa i vincoli di
 * validazione previsti.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare che i dati
 * di una definizione di gioco risultano incoerenti o incompleti.</p>
 *
 * @see RuntimeException
 */
public class InvalidGameDefinitionException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public InvalidGameDefinitionException(String message) {
        super(message);
    }
}
