package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando la definizione di un gioco non è
 * disponibile a livello locale. Si verifica quando si tenta di
 * avviare o gestire un party per una definizione di gioco
 * non presente nel server locale.
 *
 * @see com.gameplatform.local.domain.model.GameDefinition
 */
public class GameDefinitionNotAvailableLocallyException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public GameDefinitionNotAvailableLocallyException(String message) {
        super(message);
    }
}