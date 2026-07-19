package com.gameplatform.shared.domain.events;

import java.time.Instant;

/**
 * Rappresenta un evento di dominio all'interno della piattaforma di gioco.
 *
 * <p>Un evento di dominio cattura un fatto significativo accaduto nel modello di dominio e
 * ne espone i metadati essenziali per il tracciamento e l'elaborazione, quali identificativo,
 * istante di occorrenza e tipo.</p>
 *
 * @see com.gameplatform.shared.domain.events.EventPublisher
 * @see com.gameplatform.shared.domain.events.DomainEventListener
 */
public interface DomainEvent {

    /**
     * Restituisce l'identificativo univoco dell'evento.
     *
     * @return l'identificativo dell'evento come stringa non nulla e non vuota;
     *         ogni evento possiede un valore distinto utilizzato per il tracciamento
     */
    String getEventId();

    /**
     * Restituisce l'istante in cui l'evento si è verificato.
     *
     * @return l'istante di occorrenza come {@link Instant} non nullo, riferito
     *         al momento della generazione dell'evento nel dominio
     */
    Instant getOccurredAt();

    /**
     * Restituisce il tipo dell'evento.
     *
     * @return il tipo dell'evento come stringa non nulla e non vuota, che classifica
     *         la natura del fatto di dominio rappresentato
     */
    String getEventType();
}
