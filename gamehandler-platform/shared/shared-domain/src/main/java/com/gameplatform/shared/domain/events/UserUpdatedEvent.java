package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Evento di dominio che rappresenta l'aggiornamento dei dati di un utente all'interno della piattaforma.
 * Contiene le informazioni essenziali relative all'utente modificato, incluse credenziali e ruoli associati.
 *
 * @see DomainEvent
 * @see UserId
 */
public record UserUpdatedEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles) implements DomainEvent {

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
     * @return l'istante di creazione dell'evento; non è {@code null}
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento di dominio associato a questa istanza.
     *
     * @return la costante {@code "USER_UPDATED"} che identifica il tipo di evento
     */
    @Override
    public String getEventType() {
        return "USER_UPDATED";
    }
}
