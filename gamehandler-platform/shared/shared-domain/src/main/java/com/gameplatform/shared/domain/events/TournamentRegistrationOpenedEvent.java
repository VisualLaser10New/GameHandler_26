package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.TournamentId;

import java.time.Instant;

/**
 * Evento di dominio che segnala l'apertura delle iscrizioni a un torneo.
 *
 * <p>Rappresenta il momento in cui la registrazione dei partecipanti diventa disponibile
 * per il torneo identificato, ed espone l'istante di avvio delle iscrizioni.</p>
 *
 * @see DomainEvent
 * @see TournamentId
 */
public record TournamentRegistrationOpenedEvent(String eventId, Instant occurredAt, TournamentId tournamentId, Instant startsAt) implements DomainEvent {

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è vuoto
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento associato a questa istanza.
     *
     * @return la costante {@code "TOURNAMENT_REGISTRATION_OPENED"}; non è {@code null} e non è vuota
     */
    @Override
    public String getEventType() {
        return "TOURNAMENT_REGISTRATION_OPENED";
    }
}