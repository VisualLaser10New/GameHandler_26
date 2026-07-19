package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

/**
 * Repository JPA per l'accesso ai dati di avanzamento della replicazione degli eventi.
 * <p>
 * Fornisce metodi per interrogare lo stato di replicazione degli eventi verso i server locali,
 * consentendo di determinare quali eventi sono stati già propagati e quali sono ancora in sospeso.
 * </p>
 *
 * @see ReplicationProgressJpaEntity
 * @see OutboxEventJpaRepository
 */
public interface ReplicationProgressJpaRepository extends JpaRepository<ReplicationProgressJpaEntity, String> {
    /**
     * Restituisce tutti i progressi di replicazione associati all'identificativo dell'evento specificato.
     *
     * @param eventId l'identificativo univoco dell'evento (non null)
     * @return una lista di entit&agrave; di progresso replicazione, vuota se nessun progresso corrisponde
     */
    List<ReplicationProgressJpaEntity> findByEventId(String eventId);

    /**
     * Restituisce i progressi di replicazione per gli eventi i cui identificativi sono contenuti
     * nella collezione specificata e per il server indicato.
     *
     * @param eventIds la collezione di identificativi evento da cercare (non null, pu&ograve; essere vuota)
     * @param serverId l'identificativo del server locale (non null)
     * @return una lista di entit&agrave; di progresso replicazione corrispondenti, vuota se nessuna corrispondenza
     */
    List<ReplicationProgressJpaEntity> findByEventIdInAndServerId(Collection<String> eventIds, String serverId);

    /**
     * Verifica se esiste gi&agrave; un progresso di replicazione per la coppia evento-server specificata.
     *
     * @param eventId  l'identificativo dell'evento (non null)
     * @param serverId l'identificativo del server locale (non null)
     * @return {@code true} se esiste un progresso di replicazione per la coppia specificata, {@code false} altrimenti
     */
    boolean existsByEventIdAndServerId(String eventId, String serverId);
}
