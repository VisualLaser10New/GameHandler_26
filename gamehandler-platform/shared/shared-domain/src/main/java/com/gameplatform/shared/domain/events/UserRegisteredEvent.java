package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Evento di dominio che rappresenta la registrazione di un nuovo utente nella piattaforma.
 * Incapsula i dati essenziali dell'utente creato, tra cui identificativo, credenziali e ruoli associati.
 *
 * @see DomainEvent
 * @see UserId
 */
public record UserRegisteredEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles) implements DomainEvent {
    /**
     * Tipo di evento associato alla registrazione di un utente.
     * Il valore è costante e pari a {@code "USER_REGISTERED"}.
     */
    public static final String EVENT_TYPE = "USER_REGISTERED";

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento, mai {@code null}
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza dell'evento, mai {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento di dominio rappresentato da questa istanza.
     *
     * @return il tipo di evento, mai {@code null} e pari a {@value #EVENT_TYPE}
     */
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}
