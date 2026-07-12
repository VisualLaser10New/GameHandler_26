package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;

import java.time.Instant;

public record TournamentMatchScheduledEvent(String eventId, Instant occurredAt, TournamentMatchId matchId, TournamentId tournamentId, int round, int bracketPosition, String participantA, String participantB, GameType gameType, String buildingId) implements DomainEvent {
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
        return "TOURNAMENT_MATCH_SCHEDULED";
    }
}