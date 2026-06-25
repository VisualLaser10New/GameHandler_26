package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventMapper {

    public OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OutboxEvent(
            entity.getId(),
            entity.getEventType(),
            entity.getPayload(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getSentAt(),
            entity.getRetryCount()
        );
    }

    public OutboxEventJpaEntity toEntity(OutboxEvent domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxEventJpaEntity(
            domain.getId(),
            domain.getEventType(),
            domain.getPayload(),
            domain.getStatus(),
            domain.getCreatedAt(),
            domain.getSentAt(),
            domain.getRetryCount()
        );
    }
}
