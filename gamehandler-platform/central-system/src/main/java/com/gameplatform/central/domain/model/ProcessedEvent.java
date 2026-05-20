package com.gameplatform.central.domain.model;

import java.time.Instant;

public class ProcessedEvent {
    private String eventId;
    private Instant processedAt;

    public ProcessedEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }
    public Instant getProcessedAt() {
        return processedAt;
    }
}
