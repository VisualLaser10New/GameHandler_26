package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.LocalAdminBuildingMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalAdminBuildingJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link LocalAdminBuildingLocalRepository} port. Matches the
 * central-side {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-
 * injects the JPA repository + mapper; {@code save} is an upsert by composite
 * PK (the underlying {@link LocalAdminBuildingJpaRepository#save} merges an
 * existing row if the (user_id, building_id) PK is already present — idempotent
 * on re-application of the same LOCAL_ADMIN_BUILDING_ASSIGNED event).
 */
@Component
public class LocalAdminBuildingLocalRepositoryAdapter implements LocalAdminBuildingLocalRepository {

    private final LocalAdminBuildingJpaRepository jpaRepository;
    private final LocalAdminBuildingMapper mapper;

    public LocalAdminBuildingLocalRepositoryAdapter(LocalAdminBuildingJpaRepository jpaRepository,
                                                     LocalAdminBuildingMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public LocalAdminBuilding save(LocalAdminBuilding binding) {
        LocalAdminBuildingJpaEntity entity = mapper.toEntity(binding);
        LocalAdminBuildingJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId) {
        if (userId == null || buildingId == null) {
            return false;
        }
        return jpaRepository.existsByUserIdAndBuildingId(userId.value(), buildingId.id());
    }

    @Override
    @Transactional
    public void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId) {
        if (userId == null || buildingId == null) {
            return;
        }
        jpaRepository.deleteByUserIdAndBuildingId(userId.value(), buildingId.id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalAdminBuilding> findByUserId(UserId userId) {
        if (userId == null) {
            return List.of();
        }
        return jpaRepository.findByUserId(userId.value()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}