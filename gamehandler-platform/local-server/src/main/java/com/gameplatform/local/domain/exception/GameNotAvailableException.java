package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando un gioco richiesto non è al momento
 * disponibile per l'utilizzo. Impedisce l'avvio di sessioni
 * di gioco su titoli non accessibili.
 *
 * @see com.gameplatform.local.domain.model.Game
 */
public class GameNotAvailableException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public GameNotAvailableException(String message) {
        super(message);
    }
}
