package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta una transizione di stato
 * non valida per un gioco. Garantisce che le sessioni di gioco
 * seguano il flusso di stato previsto dal dominio.
 *
 * @see com.gameplatform.local.domain.model.Game
 */
public class InvalidGameStateTransitionException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public InvalidGameStateTransitionException(String message) {
        super(message);
    }
}
