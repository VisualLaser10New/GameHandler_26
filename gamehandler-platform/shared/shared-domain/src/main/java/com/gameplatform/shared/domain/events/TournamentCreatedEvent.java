package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;

public record TournamentCreatedEvent(String eventId, Instant occurredAt, TournamentId tournamentId, String name, GameType gameType, boolean teamBased, int teamSize, UserId createdBy) implements DomainEvent {
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
        return "TOURNAMENT_CREATED";
    }
}