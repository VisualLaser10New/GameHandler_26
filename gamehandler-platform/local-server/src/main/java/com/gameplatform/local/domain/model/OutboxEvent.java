package com.gameplatform.local.domain.model;

import java.time.Instant;

/**
 * Rappresenta un evento nella coda di outbox per la pubblicazione verso
 * il Central server. Gestisce il ciclo di vita dell'evento attraverso
 * gli stati PENDING, SENT e FAILED, con tracciamento del numero di
 * tentativi di invio e soglia di fallimento configurabile.
 *
 * @see OutboxEventStatus
 */
public class OutboxEvent {
    /** Soglia di tentativi di invio dopo la quale l'evento transita a FAILED. */
    public static final int FAILED_THRESHOLD = 10;

    private final String id;
    private final String eventType;
    private final String payload;
    private String status;
    private final Instant createdAt;
    private Instant sentAt;
    private int retryCount;

    /**
     * Costruisce un nuovo evento outbox.
     *
     * @param id         identificatore univoco dell'evento (non blank)
     * @param eventType  tipo dell'evento (non blank)
     * @param payload    payload dell'evento in formato JSON (non null)
     * @param status     stato iniziale (non blank)
     * @param createdAt  istante di creazione (non null)
     * @param sentAt     istante di invio (può essere null)
     * @param retryCount numero di tentativi già effettuati
     * @throws IllegalArgumentException se id, eventType o status sono blank,
     *                                  o se payload o createdAt sono null
     */
    public OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt, int retryCount) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("EventType cannot be null or empty");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.retryCount = retryCount;
    }

    /**
     * Marca l'evento come inviato con l'istante corrente.
     *
     * @see #markAsSent(Instant)
     */
    public void markAsSent() {
        markAsSent(Instant.now());
    }

    /**
     * Marca l'evento come inviato all'istante specificato.
     *
     * @param sentAt istante di invio
     */
    public void markAsSent(Instant sentAt) {
        this.status = OutboxEventStatus.SENT.name();
        this.sentAt = sentAt;
    }

    /**
     * Incrementa il contatore dei tentativi. Se viene raggiunta la soglia
     * {@link #FAILED_THRESHOLD}, l'evento transita automaticamente a FAILED.
     */
    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= FAILED_THRESHOLD) {
            this.status = OutboxEventStatus.FAILED.name();
        }
    }

    /**
     * Marca l'evento come fallito.
     */
    public void markAsFailed() {
        this.status = OutboxEventStatus.FAILED.name();
    }

    /**
     * Verifica se l'evento è nello stato FAILED.
     *
     * @return true se lo stato è FAILED
     */
    public boolean hasFailed() {
        return OutboxEventStatus.FAILED.name().equalsIgnoreCase(status);
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
     * Restituisce il tipo dell'evento.
     *
     * @return eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Restituisce il payload dell'evento.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Restituisce lo stato corrente dell'evento.
     *
     * @return status
     */
    public String getStatus() {
        return status;
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
     * Restituisce l'istante di invio dell'evento.
     *
     * @return sentAt, o null se non ancora inviato
     */
    public Instant getSentAt() {
        return sentAt;
    }

    /**
     * Restituisce il numero di tentativi di invio effettuati.
     *
     * @return retryCount
     */
    public int getRetryCount() {
        return retryCount;
    }
}

