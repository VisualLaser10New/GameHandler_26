package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando l'utente che tenta di utilizzare una
 * prenotazione non corrisponde all'utente a cui la prenotazione
 * è stata assegnata. Garantisce che solo l'intestatario possa
 * consumare la prenotazione.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 * @see com.gameplatform.local.domain.model.User
 */
public class ReservationUserMismatchException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public ReservationUserMismatchException(String message) {
        super(message);
    }
}
