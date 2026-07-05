package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameJpaRepository extends JpaRepository<GameJpaEntity, String> {
    List<GameJpaEntity> findByBuildingId(String buildingId);
    List<GameJpaEntity> findByStatus(GameMachineStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameJpaEntity g where g.id = :id")
    Optional<GameJpaEntity> findByIdForUpdate(@Param("id") String id);
}
