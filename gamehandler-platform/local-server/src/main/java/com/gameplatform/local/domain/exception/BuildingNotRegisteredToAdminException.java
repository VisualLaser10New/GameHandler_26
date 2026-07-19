package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando un edificio non risulta registrato
 * all'amministratore specificato. Impedisce operazioni di gestione
 * su edifici non associati all'amministratore richiedente.
 *
 * @see com.gameplatform.local.domain.model.Building
 */
public class BuildingNotRegisteredToAdminException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public BuildingNotRegisteredToAdminException(String message) {
        super(message);
    }
}
