package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;

public record UserRegisteredEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles) implements DomainEvent {
    public static final String EVENT_TYPE = "USER_REGISTERED";

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
