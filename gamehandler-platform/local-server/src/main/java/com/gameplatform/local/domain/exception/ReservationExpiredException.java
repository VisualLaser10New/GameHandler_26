package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si tenta di utilizzare una prenotazione
 * la cui validità temporale è scaduta. Impedisce l'accesso a
 * sessioni di gioco con prenotazioni non più valide.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 */
public class ReservationExpiredException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public ReservationExpiredException(String message) {
        super(message);
    }
}
