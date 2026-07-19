package com.gameplatform.shared.domain.events;

import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;

/**
 * Evento di dominio che segnala il completamento di un incontro di torneo.
 *
 * <p>Contiene i dati essenziali relativi all'incontro concluso, quali l'identificativo
 * dell'incontro, il torneo di appartenenza, il vincitore e lo stato finale. Il tipo
 * di evento è esposto dalla costante {@link #EVENT_TYPE}.</p>
 *
 * @see DomainEvent
 * @see TournamentMatchId
 * @see TournamentId
 * @see TournamentMatchStatus
 */
public record TournamentMatchCompletedEvent(String eventId, Instant occurredAt, TournamentMatchId matchId, TournamentId tournamentId, String winner, String resultData, TournamentMatchStatus status) implements DomainEvent {
    /**
     * Tipo di evento associato al completamento di un incontro di torneo.
     */
    public static final String EVENT_TYPE = "TOURNAMENT_MATCH_COMPLETED";

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento; non è {@code null} e non è vuoto
     * @see DomainEvent#getEventId()
     */
    @Override
    public String getEventId() {
        return eventId;
    }

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza dell'evento; non è {@code null}
     * @see DomainEvent#getOccurredAt()
     */
    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Restituisce il tipo di evento, pari alla costante {@link #EVENT_TYPE}.
     *
     * @return il tipo di evento; non è {@code null} e non è vuoto
     * @see DomainEvent#getEventType()
     */
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}