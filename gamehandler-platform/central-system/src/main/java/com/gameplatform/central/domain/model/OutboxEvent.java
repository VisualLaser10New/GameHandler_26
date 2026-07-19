package com.gameplatform.central.domain.model;

import java.time.Instant;

/**
 * Entità di dominio che rappresenta un evento persistito secondo il pattern
 * outbox, destinato alla successiva pubblicazione verso i sistemi esterni.
 * Mantiene il tipo di evento, il relativo payload serializzato, lo stato di
 * elaborazione e gli istanti di creazione e invio.
 *
 * @see OutboxEventStatus
 */
public class OutboxEvent {
    private String id;
    private String eventType;
    private String payload;
    private OutboxEventStatus status;
    private Instant createdAt;
    private Instant sentAt;

    /**
     * Costruisce un evento outbox con i valori specificati.
     *
     * @param id identificativo univoco dell'evento; non può essere {@code null} né vuoto
     * @param eventType tipo dell'evento; non può essere {@code null} né vuoto
     * @param payload contenuto serializzato dell'evento; non può essere {@code null} né vuoto
     * @param status stato di elaborazione dell'evento; non può essere {@code null}
     * @param createdAt istante di creazione dell'evento; non può essere {@code null}
     * @param sentAt istante di invio dell'evento; può essere {@code null} se non ancora inviato
     * @throws IllegalArgumentException se uno dei vincoli sui parametri non è rispettato
     */
    public OutboxEvent(String id, String eventType, String payload, OutboxEventStatus status, Instant createdAt, Instant sentAt) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("EventType cannot be null or empty");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
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
    }

    /**
     * Contrassegna l'evento come inviato, impostandone lo stato a
     * {@link OutboxEventStatus#SENT} e registrando l'istante corrente come
     * momento di invio.
     */
    public void markAsSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento, mai {@code null}
     */
    public String getId() {
        return id;
    }
    /**
     * Restituisce il tipo dell'evento.
     *
     * @return il tipo dell'evento, mai {@code null}
     */
    public String getEventType() {
        return eventType;
    }
    /**
     * Restituisce il contenuto serializzato dell'evento.
     *
     * @return il payload dell'evento, mai {@code null}
     */
    public String getPayload() {
        return payload;
    }
    /**
     * Restituisce lo stato di elaborazione corrente dell'evento.
     *
     * @return lo stato dell'evento, mai {@code null}
     * @see #markAsSent()
     */
    public OutboxEventStatus getStatus() {
        return status;
    }
    /**
     * Restituisce l'istante di creazione dell'evento.
     *
     * @return l'istante di creazione, mai {@code null}
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    /**
     * Restituisce l'istante in cui l'evento è stato inviato.
     *
     * @return l'istante di invio, oppure {@code null} se l'evento non è ancora stato inviato
     */
    public Instant getSentAt() {
        return sentAt;
    }
}

