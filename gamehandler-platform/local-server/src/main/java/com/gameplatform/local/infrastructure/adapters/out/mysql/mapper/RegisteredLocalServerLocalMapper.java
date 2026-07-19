package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link RegisteredLocalServerLocal} domain
 * model and the {@link RegisteredLocalServerLocalJpaEntity} persistence
 * entity. Mirror of {@link TournamentMatchLocalMapper}.
 */
@Component
public class RegisteredLocalServerLocalMapper {

    /**
     * Converte un'entità JPA {@link RegisteredLocalServerLocalJpaEntity} nel
     * corrispondente modello di dominio {@link RegisteredLocalServerLocal}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
    public RegisteredLocalServerLocal toDomain(RegisteredLocalServerLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RegisteredLocalServerLocal(
                new BuildingId(entity.getBuildingId()),
                entity.getBaseUrl(),
                entity.getLastSeenAt(),
                entity.getActive() != null && entity.getActive(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link RegisteredLocalServerLocal} nella
     * corrispondente entità JPA {@link RegisteredLocalServerLocalJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
    public RegisteredLocalServerLocalJpaEntity toEntity(RegisteredLocalServerLocal domain) {
        if (domain == null) {
            return null;
        }
        return new RegisteredLocalServerLocalJpaEntity(
                domain.getBuildingId().id(),
                domain.getBaseUrl(),
                domain.getLastSeenAt(),
                domain.isActive(),
                domain.getUpdatedAt()
        );
    }
}