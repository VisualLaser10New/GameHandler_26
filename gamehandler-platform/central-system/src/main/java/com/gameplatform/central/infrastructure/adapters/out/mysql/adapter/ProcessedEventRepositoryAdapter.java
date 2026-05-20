package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ProcessedEventJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.ProcessedEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository jpaRepository;

    public ProcessedEventRepositoryAdapter(ProcessedEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByEventId(String eventId) {
        if (eventId == null) {
            return false;
        }
        return jpaRepository.existsById(eventId);
    }

    @Override
    public void save(ProcessedEvent event) {
        if (event == null) {
            return;
        }
        ProcessedEventJpaEntity entity = new ProcessedEventJpaEntity(
                event.getEventId(),
                event.getProcessedAt()
        );
        jpaRepository.save(entity);
    }
}
