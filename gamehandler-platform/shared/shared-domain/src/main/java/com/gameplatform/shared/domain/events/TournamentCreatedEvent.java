package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;

/**
 * Evento di dominio emesso al momento della creazione di un torneo.
 * Rappresenta, in forma immutabile, i dati essenziali del torneo appena istanziato
 * e l'identità dell'utente che ne ha effettuato la creazione.
 *
 * @see DomainEvent
 * @see TournamentId
 * @see GameType
 * @see UserId
 */
public record TournamentCreatedEvent(String eventId, Instant occurredAt, TournamentId tournamentId, String name, GameType gameType, boolean teamBased, int teamSize, UserId createdBy) implements DomainEvent {

    /**
     * Restituisce l'identificatore univoco dell'evento.
     *
     * @return l'identificatore dell'evento; non è {@code null} e non è vuoto
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di creazione dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento, utilizzato per discriminarne la natura nel sistema.
     *
     * @return la costante {@code "TOURNAMENT_CREATED"}; non è {@code null} e non è vuota
     */
    @Override
    public String getEventType() {
        return "TOURNAMENT_CREATED";
    }
}