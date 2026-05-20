package main.java.com.gameplatform.shared.domain.events;

import main.java.com.gameplatform.shared.domain.model.BuildingId;
import main.java.com.gameplatform.shared.domain.model.GameId;
import main.java.com.gameplatform.shared.domain.model.ReservationId;
import main.java.com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;

public record ReservationCreatedEvent(String eventId, Instant occurredAt, ReservationId reservationId, GameId gameId, UserId userId, BuildingId buildingId) implements DomainEvent {
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
        return "RESERVATION_CREATED";
    }
}
