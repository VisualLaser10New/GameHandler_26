package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando un utente richiesto non viene trovato
 * nel sistema. Si verifica quando si cerca di accedere a un
 * utente con identificatore inesistente o non registrato.
 *
 * @see com.gameplatform.local.domain.model.User
 */
public class UserNotFoundException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}
