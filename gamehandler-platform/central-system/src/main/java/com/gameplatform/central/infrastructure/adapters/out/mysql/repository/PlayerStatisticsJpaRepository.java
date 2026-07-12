package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PlayerStatisticsJpaEntity}.
 *
 * <p>{@link #findByUserIdAndGameTypeForUpdate} acquires a
 * {@link LockModeType#PESSIMISTIC_WRITE pessimistic write lock}, mirroring the
 * {@code aggregated_statistics} {@code findBy...WithLock} pattern
 * ({@code StatisticsJpaRepository}). The lock serialises concurrent
 * {@code GAME_SESSION_COMPLETED} projections for the same (user, gameType) so
 * the {@code matches_played}/{@code matches_won} increment is atomic (FASE 3,
 * PIANO &sect;2.4 / protocol &sect;2.C thread-safety mandate).</p>
 */
@Repository
public interface PlayerStatisticsJpaRepository extends JpaRepository<PlayerStatisticsJpaEntity, PlayerStatisticsId> {

    List<PlayerStatisticsJpaEntity> findByUserId(String userId);

    Optional<PlayerStatisticsJpaEntity> findByUserIdAndGameType(String userId, String gameType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlayerStatisticsJpaEntity s WHERE s.userId = :userId AND s.gameType = :gameType")
    Optional<PlayerStatisticsJpaEntity> findByUserIdAndGameTypeForUpdate(
            @Param("userId") String userId,
            @Param("gameType") String gameType);
}