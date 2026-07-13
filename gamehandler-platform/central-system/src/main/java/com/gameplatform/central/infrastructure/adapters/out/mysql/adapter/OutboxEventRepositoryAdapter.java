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

@Component
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;
    private final OutboxEventMapper mapper;
    private final java.time.Clock clock;

    public OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository, OutboxEventMapper mapper, java.time.Clock clock) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = mapper.toEntity(event);
        OutboxEventJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public List<OutboxEvent> findPending() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<OutboxEvent> findPendingLimit(int limit) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING.name(), PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

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

    @Override
    @Transactional(readOnly = true)
    public long countPendingReplicationForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return 0L;
        }
        return jpaRepository.countPendingReplicationForServer(serverId);
    }
}
