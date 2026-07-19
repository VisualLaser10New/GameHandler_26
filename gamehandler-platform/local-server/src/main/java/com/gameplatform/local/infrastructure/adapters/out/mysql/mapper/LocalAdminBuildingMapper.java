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

    /**
     * Converte un'entità JPA {@link LocalAdminBuildingJpaEntity} nel corrispondente
     * modello di dominio {@link LocalAdminBuilding}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
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

    /**
     * Converte un modello di dominio {@link LocalAdminBuilding} nella corrispondente
     * entità JPA {@link LocalAdminBuildingJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
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