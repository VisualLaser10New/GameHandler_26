package com.gameplatform.central.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entità di dominio che rappresenta un evento già elaborato, utilizzata per
 * garantire l'idempotenza nel trattamento degli eventi. Registra
 * l'identificativo dell'evento e l'istante in cui è stato processato;
 * l'identità è determinata dall'identificativo dell'evento.
 */
public class ProcessedEvent {
    private String eventId;
    private Instant processedAt;

    /**
     * Costruisce un evento elaborato con i valori specificati.
     *
     * @param eventId identificativo univoco dell'evento; non può essere {@code null} né vuoto
     * @param processedAt istante in cui l'evento è stato elaborato; può essere {@code null}
     * @throws IllegalArgumentException se {@code eventId} è {@code null} o vuoto
     */
    public ProcessedEvent(String eventId, Instant processedAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be null or empty");
        }
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    /**
     * Restituisce l'identificativo univoco dell'evento elaborato.
     *
     * @return l'identificativo dell'evento, mai {@code null}
     */
    public String getEventId() {
        return eventId;
    }
    /**
     * Restituisce l'istante in cui l'evento è stato elaborato.
     *
     * @return l'istante di elaborazione, oppure {@code null} se non specificato
     */
    public Instant getProcessedAt() {
        return processedAt;
    }

    /**
     * Confronta questo evento elaborato con un altro oggetto verificandone
     * l'uguaglianza sulla base dell'identificativo dell'evento.
     *
     * @param o oggetto da confrontare; può essere {@code null}
     * @return {@code true} se l'oggetto è un {@code ProcessedEvent} con lo stesso identificativo, {@code false} altrimenti
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEvent that = (ProcessedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    /**
     * Restituisce il codice hash calcolato sull'identificativo dell'evento.
     *
     * @return il codice hash dell'evento elaborato
     */
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}

