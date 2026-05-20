package main.java.com.gameplatform.shared.domain.events;

import main.java.com.gameplatform.shared.domain.model.GameId;
import main.java.com.gameplatform.shared.domain.model.GameMachineStatus;

import java.time.Instant;

public record GameStateChangedEvent(String eventId, Instant occurredAt, GameId gameId, GameMachineStatus oldStatus, GameMachineStatus newStatus) implements DomainEvent {
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
        return "GAME_STATE_CHANGED";
    }
}
