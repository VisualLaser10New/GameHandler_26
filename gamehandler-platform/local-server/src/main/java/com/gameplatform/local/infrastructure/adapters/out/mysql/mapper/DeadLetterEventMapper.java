package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.DeadLetterEventJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterEventMapper {

    public DeadLetterEvent toDomain(DeadLetterEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DeadLetterEvent(
            entity.getId(),
            entity.getEventId(),
            entity.getEventType(),
            entity.getPayload(),
            entity.getOriginalStatus(),
            entity.getRetryCount(),
            entity.getReason(),
            entity.getPromotedAt()
        );
    }

    public DeadLetterEventJpaEntity toEntity(DeadLetterEvent domain) {
        if (domain == null) {
            return null;
        }
        return new DeadLetterEventJpaEntity(
            domain.getId(),
            domain.getEventId(),
            domain.getEventType(),
            domain.getPayload(),
            domain.getOriginalStatus(),
            domain.getRetryCount(),
            domain.getReason(),
            domain.getPromotedAt()
        );
    }
}
