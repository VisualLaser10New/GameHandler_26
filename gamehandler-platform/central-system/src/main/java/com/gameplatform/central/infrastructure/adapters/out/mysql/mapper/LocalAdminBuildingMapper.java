package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio
 * {@link LocalAdminBuilding} e l'entità persistente
 * {@link LocalAdminBuildingJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte gli identificativi
 * {@code user_id} e {@code building_id} da/verso {@link UserId} e
 * {@link BuildingId}.
 *
 * @see LocalAdminBuilding
 * @see LocalAdminBuildingJpaEntity
 */
@Component
public class LocalAdminBuildingMapper {

    /**
     * Converte un'entità persistente {@link LocalAdminBuildingJpaEntity} nel
     * corrispondente modello di dominio {@link LocalAdminBuilding}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link LocalAdminBuilding} o {@code null} se l'entità è {@code null}
     * @see #toEntity(LocalAdminBuilding)
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
     * Converte un modello di dominio {@link LocalAdminBuilding} nell'entità
     * persistente {@link LocalAdminBuildingJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link LocalAdminBuildingJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(LocalAdminBuildingJpaEntity)
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