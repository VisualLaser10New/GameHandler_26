package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.ProcessedEvent;

/**
 * Porta di persistenza per gli eventi già processati.
 *
 * <p>Consente di tracciare gli identificativi degli eventi consumati al fine di
 * garantire l'idempotenza dell'elaborazione e di evitare duplicati nella
 * proiezione degli eventi di dominio.</p>
 *
 * @see ProcessedEvent
 */
public interface ProcessedEventRepository {

    /**
     * Verifica se un evento con l'identificativo indicato risulta già processato.
     *
     * @param eventId l'identificativo dell'evento; non deve essere {@code null}
     * @return {@code true} se l'evento è già stato processato, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code eventId} è {@code null}
     */
    boolean existsByEventId(String eventId);

    /**
     * Salva la registrazione di un evento processato.
     *
     * @param event l'evento processato da registrare; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code event} è {@code null}
     */
    void save(ProcessedEvent event);
}
