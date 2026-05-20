package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import java.time.LocalDate;
import java.util.List;

public interface GetGlobalStatisticsUseCase {
    List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end);
}

