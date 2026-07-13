package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;

public record GameSessionCompletedEvent(String eventId, Instant occurredAt, GameSessionId sessionId, GameType gameType, String resultJson) implements DomainEvent {
    public static final String EVENT_TYPE = "GAME_SESSION_COMPLETED";

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
