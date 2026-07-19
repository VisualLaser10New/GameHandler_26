package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta di avviare un match di torneo
 * che non si trova nello stato SCHEDULED. Il match deve essere
 * programmato prima di poter essere avviato.
 *
 * @see com.gameplatform.local.domain.model.TournamentMatch
 */
public class TournamentMatchNotScheduledException extends RuntimeException {
    /**
     * Costruisce l'eccezione con un messaggio descrittivo.
     *
     * @param message la descrizione del match non programmato (non null)
     */
    public TournamentMatchNotScheduledException(String message) {
        super(message);
    }
}