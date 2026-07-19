package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando un match di torneo richiesto non viene
 * trovato nel repository locale del server. Si verifica quando
 * l'identificatore del match non corrisponde ad alcun record.
 *
 * @see com.gameplatform.local.domain.model.TournamentMatch
 */
public class TournamentMatchNotFoundException extends RuntimeException {
    /**
     * Costruisce l'eccezione con un messaggio descrittivo.
     *
     * @param message il dettaglio dell'errore (non null)
     */
    public TournamentMatchNotFoundException(String message) {
        super(message);
    }
}
