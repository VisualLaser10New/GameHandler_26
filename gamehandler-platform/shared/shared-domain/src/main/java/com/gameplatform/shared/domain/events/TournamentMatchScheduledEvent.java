package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;

import java.time.Instant;

/**
 * Evento di dominio che segnala la programmazione di un incontro di torneo.
 * Rappresenta l'informazione immutabile relativa a un match pianificato all'interno
 * di un torneo, inclusi i partecipanti, la fase (round e posizione nel bracket) e la sede.
 *
 * @see DomainEvent
 * @see TournamentMatchId
 * @see TournamentId
 */
public record TournamentMatchScheduledEvent(String eventId, Instant occurredAt, TournamentMatchId matchId, TournamentId tournamentId, int round, int bracketPosition, String participantA, String participantB, GameType gameType, String buildingId) implements DomainEvent {

    /**
     * Restituisce l'identificatore univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è vuoto
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante temporale in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento associato a questo record.
     *
     * @return la costante {@code "TOURNAMENT_MATCH_SCHEDULED"} che identifica il tipo di evento
     */
    @Override
    public String getEventType() {
        return "TOURNAMENT_MATCH_SCHEDULED";
    }
}