package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.domain.ports.out.DeadLetterRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.DeadLetterEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.DeadLetterEventMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.DeadLetterEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeadLetterEventRepositoryAdapter implements DeadLetterRepository {

    private final DeadLetterEventJpaRepository jpaRepository;
    private final DeadLetterEventMapper mapper;

    public DeadLetterEventRepositoryAdapter(DeadLetterEventJpaRepository jpaRepository, DeadLetterEventMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(DeadLetterEvent event) {
        DeadLetterEventJpaEntity entity = mapper.toEntity(event);
        jpaRepository.save(entity);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
