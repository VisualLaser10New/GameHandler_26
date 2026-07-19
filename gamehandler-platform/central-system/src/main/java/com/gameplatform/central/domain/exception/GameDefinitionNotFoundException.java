package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando la definizione di un gioco richiesta non puo' essere
 * individuata nel sistema.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare l'assenza di
 * una definizione di gioco corrispondente a un identificativo fornito.</p>
 *
 * @see RuntimeException
 */
public class GameDefinitionNotFoundException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public GameDefinitionNotFoundException(String message) {
        super(message);
    }
}
