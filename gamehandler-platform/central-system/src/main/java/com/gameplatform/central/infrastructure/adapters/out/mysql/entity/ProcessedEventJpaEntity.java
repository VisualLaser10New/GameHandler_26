package com.gameplatform.central.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entità JPA per la tabella {@code processed_events} del database MySQL.
 *
 * <p>Tiene traccia degli eventi di dominio già elaborati dal sistema, consentendo
 * di evitare duplicazioni durante la gestione della consegna almeno una volta
 * (at-least-once). La chiave primaria è l'identificativo dell'evento. Non sono
 * dichiarate relazioni JPA: l'evento è referenziato tramite il proprio
 * identificativo testuale.</p>
 *
 * @see OutboxEventJpaEntity
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * Costruttore di default richiesto da Hibernate per la materializzazione
     * dell'entità tramite reflection.
     */
    public ProcessedEventJpaEntity() {
    }

    /**
     * Costruisce la registrazione di un evento elaborato.
     *
     * @param eventId identificativo dell'evento elaborato; non deve essere {@code null}
     * @param processedAt istante in cui l'evento è stato elaborato; non deve essere {@code null}
     */
    public ProcessedEventJpaEntity(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    /**
     * Restituisce l'identificativo dell'evento elaborato.
     *
     * @return l'identificativo dell'evento; non deve essere {@code null}
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Imposta l'identificativo dell'evento elaborato.
     *
     * @param eventId nuovo identificativo dell'evento; può essere {@code null}
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento è stato elaborato.
     *
     * @return l'istante di elaborazione; non deve essere {@code null}
     */
    public Instant getProcessedAt() {
        return processedAt;
    }

    /**
     * Imposta l'istante in cui l'evento è stato elaborato.
     *
     * @param processedAt nuovo istante di elaborazione; non deve essere {@code null}
     */
    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
