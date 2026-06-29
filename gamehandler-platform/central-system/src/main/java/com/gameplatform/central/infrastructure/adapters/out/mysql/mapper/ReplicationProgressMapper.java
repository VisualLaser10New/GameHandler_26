package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ReplicationProgressMapper {

    public ReplicationProgress toDomain(ReplicationProgressJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReplicationProgress(entity.getEventId(), entity.getServerId());
    }

    public ReplicationProgressJpaEntity toEntity(ReplicationProgress domain) {
        if (domain == null) {
            return null;
        }
        String id = domain.eventId() + "_" + domain.serverId();
        return new ReplicationProgressJpaEntity(id, domain.eventId(), domain.serverId());
    }
}
