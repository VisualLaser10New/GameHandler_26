package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;

/**
 * Evento di dominio che notifica l'avvenuta creazione di una prenotazione.
 * Trasporta gli identificativi della prenotazione, del gioco, dell'utente e dell'edificio coinvolti,
 * insieme al momento in cui l'evento si è verificato.
 *
 * @see DomainEvent
 */
public record ReservationCreatedEvent(String eventId, Instant occurredAt, ReservationId reservationId, GameId gameId, UserId userId, BuildingId buildingId) implements DomainEvent {
    public static final String EVENT_TYPE = "RESERVATION_CREATED";

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento, mai {@code null}
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return il momento di occorrenza dell'evento, mai {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo dell'evento, corrispondente al valore costante {@code RESERVATION_CREATED}.
     *
     * @return il tipo dell'evento, mai {@code null}
     * @see #EVENT_TYPE
     */
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}
