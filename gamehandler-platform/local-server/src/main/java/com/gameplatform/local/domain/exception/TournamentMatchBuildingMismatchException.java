package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando l'edificio specificato per un match
 * di torneo non corrisponde a quello previsto. Riservata per
 * future validazioni di instradamento tra edifici.
 *
 * @see com.gameplatform.local.domain.model.TournamentMatch
 * @see com.gameplatform.local.domain.model.Building
 */
public class TournamentMatchBuildingMismatchException extends RuntimeException {
    /**
     * Costruisce l'eccezione con un messaggio descrittivo.
     *
     * @param message il dettaglio dell'errore (non null)
     */
    public TournamentMatchBuildingMismatchException(String message) {
        super(message);
    }
}