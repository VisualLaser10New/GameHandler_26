package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.OutboxEventMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;
    private final OutboxEventMapper mapper;
    private final Clock clock;

    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository, OutboxEventMapper mapper, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = mapper.toEntity(event);
        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<OutboxEvent> findPending() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc("PENDING").stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAsSent(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            OutboxEvent domain = mapper.toDomain(entity);
            domain.markAsSent();
            jpaRepository.save(mapper.toEntity(domain));
        });
    }

    @Override
    @Transactional
    public void incrementRetry(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            OutboxEvent domain = mapper.toDomain(entity);
            domain.incrementRetry();
            jpaRepository.save(mapper.toEntity(domain));
        });
    }

    @Override
    @Transactional
    public void markAsSentBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        jpaRepository.markAsSentBatch(ids, Instant.now(clock));
    }

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
