package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.ReservationId;

import java.time.Instant;

public record ReservationCancelledEvent(String eventId, Instant occurredAt, ReservationId reservationId) implements DomainEvent {
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
        return "RESERVATION_CANCELLED";
    }
}
