package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link LocalAdminBuilding} domain model and the
 * {@link LocalAdminBuildingJpaEntity} persistence entity. {@code @Component}
 * instance bean, matching the {@code GameMapper} convention.
 */
@Component
public class LocalAdminBuildingMapper {

    public LocalAdminBuilding toDomain(LocalAdminBuildingJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LocalAdminBuilding(
                new UserId(entity.getUserId()),
                new BuildingId(entity.getBuildingId()),
                entity.getAssignedAt()
        );
    }

    public LocalAdminBuildingJpaEntity toEntity(LocalAdminBuilding domain) {
        if (domain == null) {
            return null;
        }
        return new LocalAdminBuildingJpaEntity(
                domain.getUserId().value(),
                domain.getBuildingId().id(),
                domain.getAssignedAt()
        );
    }
}