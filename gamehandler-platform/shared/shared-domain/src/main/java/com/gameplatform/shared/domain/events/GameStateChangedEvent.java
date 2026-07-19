package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;

import java.time.Instant;

/**
 * Evento di dominio che segnala il cambiamento di stato di una macchina da gioco.
 *
 * <p>Rappresenta una transizione tra due stati della macchina, identificata in modo univoco
 * e corredata dall'istante in cui il cambiamento è avvenuto. Viene utilizzato per propagare
 * le variazioni di stato attraverso i componenti della piattaforma.</p>
 *
 * @see DomainEvent
 * @see GameId
 * @see GameMachineStatus
 */
public record GameStateChangedEvent(String eventId, Instant occurredAt, GameId gameId, GameMachineStatus oldStatus, GameMachineStatus newStatus) implements DomainEvent {

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è una stringa vuota
     * @see DomainEvent#getEventId()
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui il cambiamento di stato si è verificato.
     *
     * @return l'istante dell'evento; non è {@code null}
     * @see DomainEvent#getOccurredAt()
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento associato a questa transizione di stato.
     *
     * @return la stringa costante {@code "GAME_STATE_CHANGED"}; non è {@code null}
     * @see DomainEvent#getEventType()
     */
    @Override
    public String getEventType() {
        return "GAME_STATE_CHANGED";
    }
}
