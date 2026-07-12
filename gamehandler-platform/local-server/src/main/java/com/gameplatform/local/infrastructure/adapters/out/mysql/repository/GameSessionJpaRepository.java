package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * FASE 3 — all sessions in which the given user participated, via the
     * {@code session_participants} join table. Used by
     * {@code StatisticsService.getPlayerStatistics} for the on-demand local
     * computation.
     */
    @Query("SELECT DISTINCT s FROM GameSessionJpaEntity s JOIN s.participants p WHERE p.userId = :userId")
    List<GameSessionJpaEntity> findByParticipantUserId(@Param("userId") String userId);
}
