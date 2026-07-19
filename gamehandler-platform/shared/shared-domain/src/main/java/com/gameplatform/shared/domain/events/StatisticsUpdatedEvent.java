package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;

/**
 * Evento di dominio emesso quando le statistiche associate a un edificio vengono aggiornate.
 * Trasporta l'identificativo dell'edificio interessato e i metadati standard dell'evento
 * (identificativo e istante di occorrenza) definiti dal contratto {@link DomainEvent}.
 *
 * @see DomainEvent
 * @see BuildingId
 */
public record StatisticsUpdatedEvent(String eventId, Instant occurredAt, BuildingId buildingId) implements DomainEvent {

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è una stringa vuota
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
     * Restituisce il tipo dell'evento.
     *
     * @return la costante {@code "STATISTICS_UPDATED"} che classifica l'evento; non è {@code null}
     */
    @Override
    public String getEventType() {
        return "STATISTICS_UPDATED";
    }
}
