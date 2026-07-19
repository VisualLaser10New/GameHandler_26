package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import com.gameplatform.local.infrastructure.adapters.out.mysql.converter.JsonStringUnwrappingConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity per la tabella {@code outbox_dead_letter}.
 * Rappresenta un evento dell'outbox che, dopo aver esaurito i tentativi di
 * reinvio, viene promosso a dead letter per consentire l'ispezione manuale
 * e l'eventuale riprocessamento.
 *
 * @see OutboxEventJpaEntity
 */
@Entity
@Table(name = "outbox_dead_letter")
public class DeadLetterEventJpaEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonStringUnwrappingConverter.class)
    private String payload;

    @Column(name = "original_status", nullable = false, length = 20)
    private String originalStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "promoted_at", nullable = false)
    private Instant promotedAt;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public DeadLetterEventJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza di dead letter con tutti i campi.
     *
     * @param id             identificatore univoco del record dead letter
     * @param eventId        identificatore dell'evento originale
     * @param eventType      tipo dell'evento originale
     * @param payload        payload JSON dell'evento originale
     * @param originalStatus stato dell'evento al momento della promozione
     * @param retryCount     numero di tentativi di reinvio effettuati
     * @param reason         motivo della promozione a dead letter
     * @param promotedAt     istante di promozione a dead letter
     */
    public DeadLetterEventJpaEntity(String id, String eventId, String eventType, String payload,
                                    String originalStatus, int retryCount, String reason, Instant promotedAt) {
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
     * Restituisce l'identificatore univoco del record dead letter.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Imposta l'identificatore univoco del record dead letter.
     *
     * @param id nuovo identificatore
     */
    public void setId(String id) {
        this.id = id;
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
     * Imposta l'identificatore dell'evento originale.
     *
     * @param eventId nuovo identificatore evento
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
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
     * Imposta il tipo dell'evento originale.
     *
     * @param eventType nuovo tipo evento
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Restituisce il payload JSON dell'evento originale.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Imposta il payload JSON dell'evento originale.
     *
     * @param payload nuovo payload
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Restituisce lo stato dell'evento al momento della promozione.
     *
     * @return originalStatus
     */
    public String getOriginalStatus() {
        return originalStatus;
    }

    /**
     * Imposta lo stato dell'evento al momento della promozione.
     *
     * @param originalStatus nuovo stato originale
     */
    public void setOriginalStatus(String originalStatus) {
        this.originalStatus = originalStatus;
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
     * Imposta il numero di tentativi di reinvio effettuati.
     *
     * @param retryCount nuovo numero di tentativi
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * Restituisce il motivo della promozione a dead letter.
     *
     * @return reason (può essere {@code null})
     */
    public String getReason() {
        return reason;
    }

    /**
     * Imposta il motivo della promozione a dead letter.
     *
     * @param reason nuovo motivo
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Restituisce l'istante di promozione a dead letter.
     *
     * @return promotedAt
     */
    public Instant getPromotedAt() {
        return promotedAt;
    }

    /**
     * Imposta l'istante di promozione a dead letter.
     *
     * @param promotedAt nuovo istante di promozione
     */
    public void setPromotedAt(Instant promotedAt) {
        this.promotedAt = promotedAt;
    }
}
