package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando la validazione di un match di torneo
 * fallisce, ad esempio per disallineamento del numero di squadre
 * consentite o per partecipanti non conformi ai requisiti.
 *
 * @see com.gameplatform.local.domain.model.TournamentMatch
 */
public class TournamentMatchValidationException extends RuntimeException {
    /**
     * Costruisce l'eccezione con un messaggio descrittivo.
     *
     * @param message la descrizione del fallimento di validazione (non null)
     */
    public TournamentMatchValidationException(String message) {
        super(message);
    }
}