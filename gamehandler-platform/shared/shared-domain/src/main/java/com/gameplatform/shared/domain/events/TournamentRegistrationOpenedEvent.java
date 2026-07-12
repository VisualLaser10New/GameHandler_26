package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;

public record TournamentRegistrationOpenedEvent(String eventId, Instant occurredAt, TournamentId tournamentId, Instant startsAt) implements DomainEvent {
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
        return "TOURNAMENT_REGISTRATION_OPENED";
    }
}