package com.gameplatform.shared.domain.model;

/**
 * Enumera gli stati possibili di una prenotazione all'interno della piattaforma.
 * Rappresenta il ciclo di vita della prenotazione, dall'attesa di conferma
 * fino alla cancellazione o alla scadenza della stessa.
 *
 * @see com.gameplatform.shared.domain.model.Reservation
 */
public enum ReservationStatus {
    /**
     * Indica che la prenotazione è stata creata ma non è ancora stata confermata.
     * La prenotazione è in attesa di una decisione da parte del sistema o dell'operatore.
     */
    PENDING,

    /**
     * Indica che la prenotazione è stata confermata e risulta valida.
     * La prenotazione è attiva e pronta per essere onorata.
     */
    CONFIRMED,

    /**
     * Indica che la prenotazione è stata annullata dall'utente o dal sistema.
     * La prenotazione non è più valida e non viene onorata.
     */
    CANCELLED,

    /**
     * Indica che la prenotazione è scaduta senza essere confermata.
     * La prenotazione non è più valida a causa del superamento del tempo consentito.
     */
    EXPIRED
}
