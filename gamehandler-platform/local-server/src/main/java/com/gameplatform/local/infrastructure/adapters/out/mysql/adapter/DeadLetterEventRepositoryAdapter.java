package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.domain.ports.out.DeadLetterRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.DeadLetterEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.DeadLetterEventMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.DeadLetterEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter JPA per il port {@link DeadLetterRepository}.
 * Gestisce la persistenza degli eventi non recapitabili (dead-letter)
 * che non hanno potuto essere elaborati correttamente dal sistema
 * di messaggistica degli eventi.
 *
 * @see DeadLetterRepository
 * @see DeadLetterEventJpaRepository
 */
@Component
public class DeadLetterEventRepositoryAdapter implements DeadLetterRepository {

    private final DeadLetterEventJpaRepository jpaRepository;
    private final DeadLetterEventMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per gli eventi dead-letter
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public DeadLetterEventRepositoryAdapter(DeadLetterEventJpaRepository jpaRepository, DeadLetterEventMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un evento dead-letter nel database.
     *
     * @param event l'evento dead-letter da persistere
     */
    @Override
    @Transactional
    public void save(DeadLetterEvent event) {
        DeadLetterEventJpaEntity entity = mapper.toEntity(event);
        jpaRepository.save(entity);
    }

    /**
     * Restituisce il numero totale di eventi dead-letter presenti nel database.
     *
     * @return il conteggio totale degli eventi dead-letter
     */
    @Override
    public long count() {
        return jpaRepository.count();
    }
}
