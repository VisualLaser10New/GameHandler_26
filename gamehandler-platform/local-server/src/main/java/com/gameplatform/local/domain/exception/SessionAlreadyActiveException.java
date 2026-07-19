package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta di avviare una nuova sessione
 * mentre ne esiste già una attiva per lo stesso gioco o utente.
 * Previene la duplicazione delle sessioni di gioco concorrenti.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public class SessionAlreadyActiveException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public SessionAlreadyActiveException(String message) {
        super(message);
    }
}
