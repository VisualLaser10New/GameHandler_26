package com.gameplatform.local.domain.model;

import java.time.Instant;

public class DeadLetterEvent {

    private final String id;
    private final String eventId;
    private final String eventType;
    private final String payload;
    private final String originalStatus;
    private final int retryCount;
    private final String reason;
    private final Instant promotedAt;

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

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getOriginalStatus() {
        return originalStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }
}
