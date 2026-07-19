package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.local.infrastructure.adapters.out.mysql.converter.JsonStringUnwrappingConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity per la tabella {@code outbox_events}.
 * Rappresenta un evento da inviare al sistema Central secondo il pattern
 * outbox, con stato di elaborazione, payload JSON, tentativi di reinvio
 * e indicizzazione per interrogazioni per stato e data di creazione.
 *
 * @see DeadLetterEventJpaEntity
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at")
})
public class OutboxEventJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonStringUnwrappingConverter.class)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public OutboxEventJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza di evento outbox.
     *
     * @param id         identificatore univoco dell'evento
     * @param eventType  tipo di evento
     * @param payload    payload JSON dell'evento
     * @param status     stato di elaborazione dell'evento
     * @param createdAt  istante di creazione dell'evento
     * @param sentAt     istante di invio (può essere {@code null})
     * @param retryCount numero di tentativi di reinvio effettuati
     */
    public OutboxEventJpaEntity(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt, int retryCount) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.retryCount = retryCount;
    }

    /**
     * Restituisce l'identificatore univoco dell'evento.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco dell'evento.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Restituisce il tipo di evento.
     *
     * @return eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Imposta il tipo di evento.
     *
     * @param eventType nuovo tipo di evento
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Restituisce il payload JSON dell'evento.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Imposta il payload JSON dell'evento.
     *
     * @param payload nuovo payload
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Restituisce lo stato di elaborazione dell'evento.
     *
     * @return status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato di elaborazione dell'evento.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce l'istante di creazione dell'evento.
     *
     * @return createdAt
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione dell'evento.
     *
     * @param createdAt nuovo istante di creazione
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'istante di invio dell'evento.
     *
     * @return sentAt (può essere {@code null} se non ancora inviato)
     */
    public Instant getSentAt() {
        return sentAt;
    }

    /**
     * Imposta l'istante di invio dell'evento.
     *
     * @param sentAt nuovo istante di invio
     */
    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * Restituisce il numero di tentativi di reinvio effettuati.
     *
     * @return retryCount
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Imposta il numero di tentativi di reinvio.
     *
     * @param retryCount nuovo numero di tentativi
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
