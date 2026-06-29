package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.domain.ports.out.ReplicationProgressRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.ReplicationProgressMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ReplicationProgressJpaRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReplicationProgressRepositoryAdapter implements ReplicationProgressRepository {

    private final ReplicationProgressJpaRepository jpaRepository;
    private final ReplicationProgressMapper mapper;

    public ReplicationProgressRepositoryAdapter(ReplicationProgressJpaRepository jpaRepository, ReplicationProgressMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ReplicationProgress> findByEventId(String eventId) {
        if (eventId == null) {
            return List.of();
        }
        return jpaRepository.findByEventId(eventId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(ReplicationProgress progress) {
        if (progress == null) {
            return;
        }
        ReplicationProgressJpaEntity entity = mapper.toEntity(progress);
        jpaRepository.save(entity);
    }
}
