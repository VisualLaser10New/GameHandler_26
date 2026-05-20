package main.java.com.gameplatform.shared.domain.events;

import main.java.com.gameplatform.shared.domain.model.GameSessionId;
import main.java.com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;

public record GameSessionCompletedEvent(String eventId, Instant occurredAt, GameSessionId sessionId, GameType gameType, String resultJson) implements DomainEvent {
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
        return "GAME_SESSION_COMPLETED";
    }
}
