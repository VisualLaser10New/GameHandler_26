package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatisticsJpaRepository extends JpaRepository<AggregatedStatisticsJpaEntity, String> {
    Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStart(String buildingId, String gameType, LocalDate periodStart);
    List<AggregatedStatisticsJpaEntity> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(LocalDate start, LocalDate end);
}
