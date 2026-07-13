package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;

public record TournamentMatchCompletedEvent(String eventId, Instant occurredAt, TournamentMatchId matchId, TournamentId tournamentId, String winner, String resultData, TournamentMatchStatus status) implements DomainEvent {
    public static final String EVENT_TYPE = "TOURNAMENT_MATCH_COMPLETED";

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