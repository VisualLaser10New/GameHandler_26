package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta di registrare un utente
 * già presente nel sistema. Impedisce la creazione di account
 * duplicati per lo stesso identificativo.
 *
 * @see com.gameplatform.local.domain.model.User
 */
public class UserAlreadyExistsException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Costruisce un'eccezione con il messaggio di dettaglio e la causa specificati.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     * @param cause   la causa originaria dell'eccezione
     */
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
