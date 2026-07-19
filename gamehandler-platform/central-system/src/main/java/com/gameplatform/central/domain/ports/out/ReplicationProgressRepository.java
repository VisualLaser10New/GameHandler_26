package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.ReplicationProgress;
import java.util.List;

/**
 * Porta di persistenza per l'avanzamento di replicazione degli eventi verso i
 * server locali.
 *
 * <p>Consente di registrare e consultare lo stato di avanzamento della
 * replicazione di ciascun evento per ogni server, a supporto del tracciamento
 * del backlog e dell'idempotenza dell'invio.</p>
 *
 * @see ReplicationProgress
 */
public interface ReplicationProgressRepository {

    /**
     * Restituisce tutti i record di avanzamento relativi all'evento indicato.
     *
     * @param eventId l'identificativo dell'evento; non deve essere {@code null}
     * @return la lista dei record di avanzamento per l'evento; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se {@code eventId} è {@code null}
     */
    List<ReplicationProgress> findByEventId(String eventId);

    /**
     * Salva o aggiorna un record di avanzamento di replicazione.
     *
     * @param progress il record di avanzamento da persistere; non deve essere {@code null}
     * @throws IllegalArgumentException se {@code progress} è {@code null}
     */
    void save(ReplicationProgress progress);

    /**
     * Verifica l'esistenza di un record di avanzamento per l'evento e il server indicati.
     *
     * @param eventId  l'identificativo dell'evento; non deve essere {@code null}
     * @param serverId l'identificativo del server; non deve essere {@code null}
     * @return {@code true} se esiste un record per la coppia evento-server, {@code false} altrimenti
     * @throws IllegalArgumentException se {@code eventId} o {@code serverId} sono {@code null}
     */
    boolean existsByEventIdAndServerId(String eventId, String serverId);
}
