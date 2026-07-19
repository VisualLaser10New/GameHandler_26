package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.OutboxEventStatus;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.OutboxEventMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link OutboxEventRepository}.
 * Gestisce la persistenza e la gestione del ciclo di vita degli eventi
 * della outbox, inclusi il recupero degli eventi in sospeso, la
 * marcatura come inviati/falliti e la gestione dei tentativi di
 * reinvio con soglia di fallimento.
 *
 * @see OutboxEventRepository
 * @see OutboxEventJpaRepository
 */
@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;
    private final OutboxEventMapper mapper;
    private final Clock clock;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per gli eventi della outbox
     * @param mapper        mapper per la conversione tra entity e dominio
     * @param clock         orologio per la generazione dei timestamp
     */
    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository, OutboxEventMapper mapper, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Salva un evento della outbox nel database.
     *
     * @param event l'evento della outbox da salvare
     * @return l'evento persistito
     */
    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = mapper.toEntity(event);
        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera tutti gli eventi della outbox in stato PENDING,
     * ordinati per data di creazione crescente.
     *
     * @return una lista di eventi in sospeso
     */
    @Override
    public List<OutboxEvent> findPending() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Recupera un numero limitato di eventi della outbox in stato PENDING,
     * ordinati per data di creazione crescente.
     *
     * @param limit il numero massimo di eventi da recuperare
     * @return una lista di eventi in sospeso fino al limite specificato, vuota se il limite è <= 0
     */
    @Override
    public List<OutboxEvent> findPendingLimit(int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name(), PageRequest.of(0, limit)).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Marca un evento della outbox come fallito.
     *
     * @param id l'identificativo dell'evento da marcare come fallito; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void markAsFailed(String id) {
        if (id == null) {
            return;
        }
        jpaRepository.findById(id).ifPresent(entity -> {
            OutboxEvent domain = mapper.toDomain(entity);
            domain.markAsFailed();
            jpaRepository.save(mapper.toEntity(domain));
        });
    }

    /**
     * Marca un evento della outbox come inviato.
     *
     * @param id l'identificativo dell'evento da marcare come inviato
     */
    @Override
    @Transactional
    public void markAsSent(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            OutboxEvent domain = mapper.toDomain(entity);
            domain.markAsSent();
            jpaRepository.save(mapper.toEntity(domain));
        });
    }

    /**
     * Incrementa il contatore di tentativi di reinvio per un evento della outbox.
     *
     * @param id l'identificativo dell'evento di cui incrementare il contatore
     */
    @Override
    @Transactional
    public void incrementRetry(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            OutboxEvent domain = mapper.toDomain(entity);
            domain.incrementRetry();
            jpaRepository.save(mapper.toEntity(domain));
        });
    }

    /**
     * Marca una lista di eventi della outbox come inviati in un'unica operazione batch.
     *
     * @param ids la lista degli identificativi degli eventi da marcare come inviati; se nulla o vuota l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void markAsSentBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jpaRepository.markAsSentBatch(ids, Instant.now(clock));
    }

    /**
     * Incrementa il contatore di tentativi per una lista di eventi in modalità batch.
     * Se il numero di tentativi supera la soglia configurata, gli eventi vengono
     * marcati come falliti.
     *
     * @param ids la lista degli identificativi degli eventi da elaborare; se nulla o vuota l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void incrementRetryBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jpaRepository.incrementRetryBatch(ids);
        jpaRepository.markAsFailedAboveThreshold(ids, OutboxEvent.FAILED_THRESHOLD);
    }
}
