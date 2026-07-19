package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;

/**
 * Evento di dominio che segnala il completamento di una sessione di gioco.
 *
 * <p>Incapsula i dati identificativi e descrittivi di una sessione terminata, tra cui
 * l'identificatore dell'evento, il momento in cui si è verificato, la sessione di riferimento,
 * il tipo di gioco e il risultato finale serializzato. L'evento viene utilizzato per propagare
 * l'informazione di completamento tra i componenti della piattaforma.</p>
 *
 * @see DomainEvent
 * @see GameSessionId
 * @see GameType
 */
public record GameSessionCompletedEvent(String eventId, Instant occurredAt, GameSessionId sessionId, GameType gameType, String resultJson) implements DomainEvent {
    /**
     * Costante che identifica univocamente il tipo di evento.
     */
    public static final String EVENT_TYPE = "GAME_SESSION_COMPLETED";

    /**
     * Restituisce l'identificatore univoco dell'evento.
     *
     * @return l'identificatore dell'evento; non è {@code null} e non è una stringa vuota
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
     * @return la costante {@value #EVENT_TYPE} che identifica il tipo di evento; non è {@code null}
     * @see #EVENT_TYPE
     */
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}
