package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatisticsRepository {
    AggregatedStatistics save(AggregatedStatistics stats);
    Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart);
    List<AggregatedStatistics> findByPeriod(LocalDate start, LocalDate end);
}

