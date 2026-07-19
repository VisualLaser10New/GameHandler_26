package com.gameplatform.central.domain.model;

/**
 * Enumerazione che rappresenta gli stati possibili di un evento outbox durante
 * il suo ciclo di vita, dalla creazione fino all'esito dell'invio.
 *
 * @see OutboxEvent
 */
public enum OutboxEventStatus {
    /** L'evento è in attesa di essere inviato. */
    PENDING,
    /** L'evento è stato inviato con successo. */
    SENT,
    /** L'invio dell'evento è fallito. */
    FAILED
}
