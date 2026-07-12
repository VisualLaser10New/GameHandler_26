package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link AdminRequestLocal} domain model
 * and the {@link AdminRequestLocalJpaEntity} persistence entity. Mirror
 * of {@link TournamentMatchLocalMapper}.
 */
@Component
public class AdminRequestLocalMapper {

    public AdminRequestLocal toDomain(AdminRequestLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AdminRequestLocal(
                entity.getRequestId(),
                entity.getEventType(),
                entity.getActingUserId(),
                entity.getActingRole(),
                entity.getBuildingId(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getResultData(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                entity.getOutboxEventId()
        );
    }

    public AdminRequestLocalJpaEntity toEntity(AdminRequestLocal domain) {
        if (domain == null) {
            return null;
        }
        return new AdminRequestLocalJpaEntity(
                domain.getRequestId(),
                domain.getEventType(),
                domain.getActingUserId(),
                domain.getActingRole(),
                domain.getBuildingId(),
                domain.getPayloadJson(),
                domain.getStatus(),
                domain.getResultDataJson(),
                domain.getCreatedAt(),
                domain.getCompletedAt(),
                domain.getOutboxEventId()
        );
    }
}