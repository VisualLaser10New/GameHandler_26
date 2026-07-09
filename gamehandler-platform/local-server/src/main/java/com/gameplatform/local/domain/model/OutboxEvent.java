package com.gameplatform.local.domain.model;

import java.time.Instant;

public class OutboxEvent {
    /** Retry count threshold after which an event transitions to FAILED status. */
    public static final int FAILED_THRESHOLD = 10;

    private final String id;
    private final String eventType;
    private final String payload;
    private String status;
    private final Instant createdAt;
    private Instant sentAt;
    private int retryCount;

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

    public void markAsSent() {
        markAsSent(Instant.now());
    }

    public void markAsSent(Instant sentAt) {
        this.status = "SENT";
        this.sentAt = sentAt;
    }

    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= FAILED_THRESHOLD) {
            this.status = "FAILED";
        }
    }

    public void markAsFailed() {
        this.status = "FAILED";
    }

    public boolean hasFailed() {
        return "FAILED".equalsIgnoreCase(status);
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

    public int getRetryCount() {
        return retryCount;
    }
}

