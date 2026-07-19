package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code admin_requests_local} (PIANO §7.B). PK
 * {@code request_id} (UUID); the row is written atomically with the
 * matching {@link OutboxEventJpaEntity} (the {@code outbox_event_id}
 * equals the {@code request_id}). Lifecycle:
 * {@code PENDING → COMPLETED} via {@code markCompleted} (called by the
 * matching {@code *SyncService} when the Central return-event arrives
 * carrying {@code originatingRequestId}); {@code PENDING → FAILED} via
 * {@code markFailed} (called by {@code AdminRequestTimeoutService}).
 * Both transitions are conditional {@code WHERE status = 'PENDING'} —
 * idempotent on re-delivery.
 */
@Entity
@Table(name = "admin_requests_local", indexes = {
        @Index(name = "idx_arl_user_status", columnList = "acting_user_id, status"),
        @Index(name = "idx_arl_status_created", columnList = "status, created_at")
})
public class AdminRequestLocalJpaEntity {

    @Id
    @Column(name = "request_id", length = 36, nullable = false)
    private String requestId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "acting_user_id", length = 64, nullable = false)
    private String actingUserId;

    @Column(name = "acting_role", length = 32, nullable = false)
    private String actingRole;

    @Column(name = "building_id", length = 64)
    private String buildingId;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "outbox_event_id", length = 64)
    private String outboxEventId;

    /**
     * Costruttore predefinito richiesto da JPA.
     */
    public AdminRequestLocalJpaEntity() {
    }

    /**
     * Costruisce una nuova istanza con tutti i campi.
     *
     * @param requestId    identificatore univoco della richiesta
     * @param eventType    tipo di evento amministrativo
     * @param actingUserId identificativo dell'utente che ha effettuato la richiesta
     * @param actingRole   ruolo dell'utente al momento della richiesta
     * @param buildingId   identificativo dell'edificio (opzionale)
     * @param payload      payload JSON della richiesta
     * @param status       stato corrente della richiesta (PENDING, COMPLETED, FAILED)
     * @param resultData   dati di risultato (opzionale)
     * @param createdAt    istante di creazione della richiesta
     * @param completedAt  istante di completamento (opzionale)
     * @param outboxEventId identificativo dell'evento outbox associato
     */
    public AdminRequestLocalJpaEntity(String requestId, String eventType, String actingUserId, String actingRole,
                                       String buildingId, String payload, String status, String resultData,
                                       Instant createdAt, Instant completedAt, String outboxEventId) {
        this.requestId = requestId;
        this.eventType = eventType;
        this.actingUserId = actingUserId;
        this.actingRole = actingRole;
        this.buildingId = buildingId;
        this.payload = payload;
        this.status = status;
        this.resultData = resultData;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.outboxEventId = outboxEventId;
    }

    /**
     * Restituisce l'identificativo univoco della richiesta.
     *
     * @return requestId
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Imposta l'identificativo univoco della richiesta.
     *
     * @param requestId nuovo identificativo richiesta
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Restituisce il tipo di evento amministrativo.
     *
     * @return eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Imposta il tipo di evento amministrativo.
     *
     * @param eventType nuovo tipo di evento
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Restituisce l'identificativo dell'utente agente.
     *
     * @return actingUserId
     */
    public String getActingUserId() {
        return actingUserId;
    }

    /**
     * Imposta l'identificativo dell'utente agente.
     *
     * @param actingUserId nuovo identificativo utente agente
     */
    public void setActingUserId(String actingUserId) {
        this.actingUserId = actingUserId;
    }

    /**
     * Restituisce il ruolo dell'utente agente.
     *
     * @return actingRole
     */
    public String getActingRole() {
        return actingRole;
    }

    /**
     * Imposta il ruolo dell'utente agente.
     *
     * @param actingRole nuovo ruolo agente
     */
    public void setActingRole(String actingRole) {
        this.actingRole = actingRole;
    }

    /**
     * Restituisce l'identificativo dell'edificio associato.
     *
     * @return buildingId (può essere {@code null})
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Imposta l'identificativo dell'edificio associato.
     *
     * @param buildingId nuovo identificativo edificio
     */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * Restituisce il payload JSON della richiesta.
     *
     * @return payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Imposta il payload JSON della richiesta.
     *
     * @param payload nuovo payload della richiesta
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Restituisce lo stato corrente della richiesta.
     *
     * @return status (PENDING, COMPLETED, FAILED)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Imposta lo stato corrente della richiesta.
     *
     * @param status nuovo stato
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Restituisce i dati di risultato della richiesta.
     *
     * @return resultData (può essere {@code null})
     */
    public String getResultData() {
        return resultData;
    }

    /**
     * Imposta i dati di risultato della richiesta.
     *
     * @param resultData nuovi dati di risultato
     */
    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    /**
     * Restituisce l'istante di creazione della richiesta.
     *
     * @return createdAt
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Imposta l'istante di creazione della richiesta.
     *
     * @param createdAt nuovo istante di creazione
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Restituisce l'istante di completamento della richiesta.
     *
     * @return completedAt (può essere {@code null} se non ancora completata)
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * Imposta l'istante di completamento della richiesta.
     *
     * @param completedAt nuovo istante di completamento
     */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Restituisce l'identificativo dell'evento outbox associato.
     *
     * @return outboxEventId (può essere {@code null})
     */
    public String getOutboxEventId() {
        return outboxEventId;
    }

    /**
     * Imposta l'identificativo dell'evento outbox associato.
     *
     * @param outboxEventId nuovo identificativo evento outbox
     */
    public void setOutboxEventId(String outboxEventId) {
        this.outboxEventId = outboxEventId;
    }
}
