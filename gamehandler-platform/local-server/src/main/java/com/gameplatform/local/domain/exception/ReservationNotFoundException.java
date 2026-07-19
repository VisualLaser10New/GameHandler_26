package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando una prenotazione richiesta non viene
 * trovata nel sistema. Si verifica quando si cerca di accedere
 * a una prenotazione con identificatore inesistente.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 */
public class ReservationNotFoundException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
