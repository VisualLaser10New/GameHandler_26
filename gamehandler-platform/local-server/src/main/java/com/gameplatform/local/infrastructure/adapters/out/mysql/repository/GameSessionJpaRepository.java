package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionJpaRepository extends JpaRepository<GameSessionJpaEntity, String> {
    List<GameSessionJpaEntity> findByBuildingId(String buildingId);
    List<GameSessionJpaEntity> findByStatus(String status);
    List<GameSessionJpaEntity> findByGameType(String gameType);
    Optional<GameSessionJpaEntity> findFirstByGameIdAndStatusIn(String gameId, Collection<String> statuses);
    List<GameSessionJpaEntity> findByStatusIn(Collection<String> statuses);
}
