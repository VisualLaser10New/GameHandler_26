package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameJpaRepository extends JpaRepository<GameJpaEntity, String> {
    List<GameJpaEntity> findByBuildingId(String buildingId);
    List<GameJpaEntity> findByStatus(GameMachineStatus status);
}
