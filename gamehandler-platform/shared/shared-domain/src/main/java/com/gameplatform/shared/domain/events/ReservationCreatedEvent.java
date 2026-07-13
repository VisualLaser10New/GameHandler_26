package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;

public record ReservationCreatedEvent(String eventId, Instant occurredAt, ReservationId reservationId, GameId gameId, UserId userId, BuildingId buildingId) implements DomainEvent {
    public static final String EVENT_TYPE = "RESERVATION_CREATED";

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
        return EVENT_TYPE;
    }
}
