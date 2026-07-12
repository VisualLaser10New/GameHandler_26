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

    public AdminRequestLocalJpaEntity() {
    }

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getActingUserId() {
        return actingUserId;
    }

    public void setActingUserId(String actingUserId) {
        this.actingUserId = actingUserId;
    }

    public String getActingRole() {
        return actingRole;
    }

    public void setActingRole(String actingRole) {
        this.actingRole = actingRole;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(String outboxEventId) {
        this.outboxEventId = outboxEventId;
    }
}
