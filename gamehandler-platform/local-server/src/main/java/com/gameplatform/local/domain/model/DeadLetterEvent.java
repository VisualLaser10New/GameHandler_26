package com.gameplatform.local.domain.model;

import java.time.Instant;

/**
 * Rappresenta un evento fallito che ha superato la soglia di retry ed è stato
 * spostato nella coda dei messaggi non recapitabili (dead-letter queue).
 * Contiene i dettagli dell'evento originale, il numero di tentativi effettuati
 * e la ragione del fallimento. POJO immutabile.
 */
public class DeadLetterEvent {

    private final String id;
    private final String eventId;
    private final String eventType;
    private final String payload;
    private final String originalStatus;
    private final int retryCount;
    private final String reason;
    private final Instant promotedAt;

    /**
     * Costruisce un nuovo evento dead-letter.
     *
     * @param id             identificatore univoco del record dead-letter (non blank)
     * @param eventId        identificatore dell'evento originale (non blank)
     * @param eventType      tipo dell'evento originale (non blank)
     * @param payload        payload dell'evento originale (non null)
     * @param originalStatus stato dell'evento originale al momento del fallimento (non blank)
     * @param retryCount     numero di tentativi di invio effettuati
     * @param reason         ragione del fallimento (non blank)
     * @param promotedAt     istante in cui l'evento è stato promosso a dead-letter (non null)
     * @throws IllegalArgumentException se id, eventId, eventType, originalStatus o reason sono blank,
     *                                  o se payload o promotedAt sono null
     */
    public DeadLetterEvent(String id, String eventId, String eventType, String payload,
                           String originalStatus, int retryCount, String reason, Instant promotedAt) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("EventId cannot be null or empty");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("EventType cannot be null or empty");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (originalStatus == null || originalStatus.isBlank()) {
            throw new IllegalArgumentException("OriginalStatus cannot be null or empty");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason cannot be null or empty");
        }
        if (promotedAt == null) {
            throw new IllegalArgumentException("PromotedAt cannot be null");
        }
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.originalStatus = originalStatus;
        this.retryCount = retryCount;
        this.reason = reason;
        this.promotedAt = promotedAt;
    }

    /**
     * Restituisce l'identificatore univoco del record dead-letter.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Restituisce l'identificatore dell'evento originale.
     *
     * @return eventId
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce il tipo dell'evento originale.
     *
     * @return eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Restituisce il payload dell'evento originale.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Restituisce lo stato dell'evento originale al momento del fallimento.
     *
     * @return originalStatus
     */
    public String getOriginalStatus() {
        return originalStatus;
    }

    /**
     * Restituisce il numero di tentativi di invio effettuati.
     *
     * @return retryCount
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * Restituisce la ragione del fallimento.
     *
     * @return reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Restituisce l'istante in cui l'evento è stato promosso a dead-letter.
     *
     * @return promotedAt
     */
    public Instant getPromotedAt() {
        return promotedAt;
    }
}
