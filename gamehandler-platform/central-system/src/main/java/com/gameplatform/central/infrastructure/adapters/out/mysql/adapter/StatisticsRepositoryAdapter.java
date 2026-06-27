package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.StatisticsMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.StatisticsJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class StatisticsRepositoryAdapter implements StatisticsRepository {

    private final StatisticsJpaRepository jpaRepository;
    private final StatisticsMapper mapper;

    public StatisticsRepositoryAdapter(StatisticsJpaRepository jpaRepository, StatisticsMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AggregatedStatistics save(AggregatedStatistics stats) {
        AggregatedStatisticsJpaEntity entity = mapper.toEntity(stats);
        AggregatedStatisticsJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart) {
        if (buildingId == null || gameType == null || periodStart == null) {
            return Optional.empty();
        }
        return jpaRepository.findByBuildingIdAndGameTypeAndPeriodStart(buildingId.id(), gameType.name(), periodStart)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriodWithLock(BuildingId buildingId, GameType gameType, LocalDate periodStart) {
        if (buildingId == null || gameType == null || periodStart == null) {
            return Optional.empty();
        }
        return jpaRepository.findByBuildingIdAndGameTypeAndPeriodStartWithLock(buildingId.id(), gameType.name(), periodStart)
                .map(mapper::toDomain);
    }

    @Override
    public List<AggregatedStatistics> findByPeriod(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end) {
        String buildingIdStr = buildingId != null ? buildingId.id() : null;
        String gameTypeStr = gameType != null ? gameType.name() : null;
        return jpaRepository.findByCriteria(buildingIdStr, gameTypeStr, start, end).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
