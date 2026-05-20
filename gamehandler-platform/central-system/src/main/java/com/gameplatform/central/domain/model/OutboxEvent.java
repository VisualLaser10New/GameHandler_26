package com.gameplatform.central.domain.model;

import java.time.Instant;

public class OutboxEvent {
    private String id;
    private String eventType;
    private String payload;
    private String status;
    private Instant createdAt;
    private Instant sentAt;

    public OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
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
    public String getStatus() {
        return status;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getSentAt() {
        return sentAt;
    }
}
