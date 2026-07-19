package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.ReplicationProgressMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ReplicationProgressJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA che implementa il port {@link ReplicationProgressRepository} per la
 * persistenza dello stato di avanzamento della replicazione degli eventi verso i
 * server locali su MySQL.
 *
 * @see ReplicationProgressRepository
 */
@Component
public class ReplicationProgressRepositoryAdapter implements ReplicationProgressRepository {

    private final ReplicationProgressJpaRepository jpaRepository;
    private final ReplicationProgressMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei progressi di replicazione.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di progresso
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public ReplicationProgressRepositoryAdapter(ReplicationProgressJpaRepository jpaRepository, ReplicationProgressMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Restituisce l'elenco dei progressi di replicazione associati a un evento.
     *
     * @param eventId l'identificativo dell'evento; se {@code null} restituisce una lista vuota
     * @return la lista dei progressi di replicazione; lista vuota se non ve ne sono o se {@code eventId} &egrave; {@code null}
     * @see ReplicationProgressJpaRepository#findByEventId
     */
    @Override
    public List<ReplicationProgress> findByEventId(String eventId) {
        if (eventId == null) {
            return List.of();
        }
        return jpaRepository.findByEventId(eventId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Salva (o aggiorna) un progresso di replicazione.
     *
     * @param progress il progresso da persistere; se {@code null} il metodo non effettua alcuna operazione
     * @see ReplicationProgressJpaRepository#save
     */
    @Override
    public void save(ReplicationProgress progress) {
        if (progress == null) {
            return;
        }
        ReplicationProgressJpaEntity entity = mapper.toEntity(progress);
        jpaRepository.save(entity);
    }

    /**
     * Verifica l'esistenza di un progresso di replicazione per una coppia evento-server.
     *
     * @param eventId  l'identificativo dell'evento; se {@code null} restituisce {@code false}
     * @param serverId l'identificativo del server; se {@code null} restituisce {@code false}
     * @return {@code true} se esiste il progresso, {@code false} altrimenti
     * @see ReplicationProgressJpaRepository#existsByEventIdAndServerId
     */
    @Override
    public boolean existsByEventIdAndServerId(String eventId, String serverId) {
        if (eventId == null || serverId == null) {
            return false;
        }
        return jpaRepository.existsByEventIdAndServerId(eventId, serverId);
    }
}
