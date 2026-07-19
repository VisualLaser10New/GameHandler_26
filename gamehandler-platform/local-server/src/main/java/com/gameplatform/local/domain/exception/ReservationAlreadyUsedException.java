package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta di utilizzare una prenotazione
 * già precedentemente consumata. Previene il riutilizzo fraudolento
 * o erroneo delle prenotazioni.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 */
public class ReservationAlreadyUsedException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public ReservationAlreadyUsedException(String message) {
        super(message);
    }
}
