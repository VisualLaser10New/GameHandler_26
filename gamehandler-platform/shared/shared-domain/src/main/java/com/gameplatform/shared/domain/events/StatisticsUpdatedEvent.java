package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.BuildingId;

import java.time.Instant;

public record StatisticsUpdatedEvent(String eventId, Instant occurredAt, BuildingId buildingId) implements DomainEvent {
    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return "STATISTICS_UPDATED";
    }
}
