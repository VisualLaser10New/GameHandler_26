package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.OutboxEventMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA che implementa il port {@link OutboxEventRepository} per la
 * persistenza degli eventi outbox su MySQL.
 *
 * @see OutboxEventRepository
 */
@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;
    private final OutboxEventMapper mapper;
    private final java.time.Clock clock;

    /**
     * Costruisce l'adapter iniettando il repository JPA, il mapper e l'orologio di sistema.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; outbox
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     * @param clock         orologio per la generazione dei timestamp di invio
     */
    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository, OutboxEventMapper mapper, java.time.Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Salva (o aggiorna) un evento outbox e restituisce l'entit&agrave; persistita.
     *
     * @param event l'evento outbox da persistere; non deve essere {@code null}
     * @return l'evento outbox salvato, con eventuali valorizzazioni gestite dal database
     * @see OutboxEventJpaRepository#save
     */
    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = mapper.toEntity(event);
        OutboxEventJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce l'elenco di tutti gli eventi outbox in stato pendente, ordinati per data di creazione crescente.
     *
     * @return la lista degli eventi pendenti; lista vuota se non ve ne sono
     * @see OutboxEventJpaRepository#findByStatusOrderByCreatedAtAsc
     */
    @Override
    public List<OutboxEvent> findPending() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Restituisce al pi&ugrave; {@code limit} eventi outbox in stato pendente, ordinati per data di creazione crescente.
     *
     * @param limit il numero massimo di eventi da restituire; deve essere maggiore di {@code 0}
     * @return la lista degli eventi pendenti (al pi&ugrave; {@code limit}); lista vuota se non ve ne sono
     * @see OutboxEventJpaRepository#findByStatusOrderByCreatedAtAsc
     */
    @Override
    public List<OutboxEvent> findPendingLimit(int limit) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name(), PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Marca l'evento identificato come inviato, valorizzandone il timestamp di invio.
     *
     * @param id l'identificativo dell'evento da marcare come inviato; se {@code null} il metodo non effettua alcuna operazione
     * @see OutboxEventJpaRepository#findById
     */
    @Override
    @Transactional
    public void markAsSent(String id) {
        if (id == null) {
            return;
        }
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(OutboxEventStatus.SENT.name());
            entity.setSentAt(Instant.now(clock));
            jpaRepository.save(entity);
        });
    }

    /**
     * Marca l'evento identificato come fallito.
     *
     * @param id l'identificativo dell'evento da marcare come fallito; se {@code null} il metodo non effettua alcuna operazione
     * @see OutboxEventJpaRepository#findById
     */
    @Override
    @Transactional
    public void markAsFailed(String id) {
        if (id == null) {
            return;
        }
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(OutboxEventStatus.FAILED.name());
            jpaRepository.save(entity);
        });
    }

    /**
     * Restituisce il numero di eventi di replica pendenti destinati al server indicato.
     *
     * @param serverId l'identificativo del server; se {@code null} o vuoto restituisce {@code 0}
     * @return il numero di eventi pendenti per il server; {@code 0} se non ve ne sono o se {@code serverId} &egrave; {@code null}/vuoto
     */
    @Override
    @Transactional(readOnly = true)
    public long countPendingReplicationForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return 0L;
        }
        return jpaRepository.countPendingReplicationForServer(serverId);
    }
}
