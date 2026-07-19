package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.ReservationId;

import java.time.Instant;

/**
 * Evento di dominio che segnala l'annullamento di una prenotazione all'interno della piattaforma.
 * Rispetta il contratto definito da {@link DomainEvent} e trasporta l'identificativo della
 * prenotazione interessata insieme ai metadati temporali dell'evento.
 *
 * @see DomainEvent
 * @see ReservationId
 */
public record ReservationCancelledEvent(String eventId, Instant occurredAt, ReservationId reservationId) implements DomainEvent {

    /**
     * Costante che identifica univocamente il tipo di evento come annullamento di prenotazione.
     */
    public static final String EVENT_TYPE = "RESERVATION_CANCELLED";

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è vuoto
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento, utilizzato per discriminare le differenti tipologie di eventi di dominio.
     *
     * @return la costante {@value #EVENT_TYPE} che rappresenta un annullamento di prenotazione;
     *         non è {@code null} e non è vuota
     */
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}
