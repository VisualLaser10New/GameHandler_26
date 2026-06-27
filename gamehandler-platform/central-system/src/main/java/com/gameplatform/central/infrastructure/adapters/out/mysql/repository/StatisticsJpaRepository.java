package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatisticsJpaRepository extends JpaRepository<AggregatedStatisticsJpaEntity, String> {
    Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStart(String buildingId, String gameType, LocalDate periodStart);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AggregatedStatisticsJpaEntity s WHERE s.buildingId = :buildingId AND s.gameType = :gameType AND s.periodStart = :periodStart")
    Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStartWithLock(
            @Param("buildingId") String buildingId,
            @Param("gameType") String gameType,
            @Param("periodStart") LocalDate periodStart);

    List<AggregatedStatisticsJpaEntity> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(LocalDate start, LocalDate end);

    @Query("SELECT s FROM AggregatedStatisticsJpaEntity s " +
           "WHERE (:buildingId IS NULL OR s.buildingId = :buildingId) " +
           "AND (:gameType IS NULL OR s.gameType = :gameType) " +
           "AND (:start IS NULL OR s.periodStart >= :start) " +
           "AND (:end IS NULL OR s.periodEnd <= :end)")
    List<AggregatedStatisticsJpaEntity> findByCriteria(
            @Param("buildingId") String buildingId,
            @Param("gameType") String gameType,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
