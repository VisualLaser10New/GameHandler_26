package com.gameplatform.central.domain.model;

import java.time.Instant;

public class OutboxEvent {
    private String id;
    private String eventType;
    private String payload;
    private OutboxEventStatus status;
    private Instant createdAt;
    private Instant sentAt;

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

    public void markAsSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = Instant.now();
    }

    public String getId() {
        return id;
    }
    public String getEventType() {
        return eventType;
    }
    public String getPayload() {
        return payload;
    }
    public OutboxEventStatus getStatus() {
        return status;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getSentAt() {
        return sentAt;
    }
}

