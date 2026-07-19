package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;

/**
 * Evento di dominio che segnala il completamento di un torneo sulla piattaforma.
 *
 * <p>Rappresenta il momento in cui un torneo termina e contiene l'identificativo
 * dell'evento, l'istante in cui si è verificato e il riferimento al torneo concluso.</p>
 *
 * @see DomainEvent
 * @see TournamentId
 */
public record TournamentCompletedEvent(String eventId, Instant occurredAt, TournamentId tournamentId) implements DomainEvent {

    /**
     * Restituisce l'identificativo univoco di questo evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è vuoto
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui il torneo è stato completato.
     *
     * @return l'istante di occorrenza dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di questo evento di dominio.
     *
     * @return la costante {@code "TOURNAMENT_COMPLETED"} che identifica il tipo di evento;
     *         non è {@code null} e non è vuota
     */
    @Override
    public String getEventType() {
        return "TOURNAMENT_COMPLETED";
    }
}