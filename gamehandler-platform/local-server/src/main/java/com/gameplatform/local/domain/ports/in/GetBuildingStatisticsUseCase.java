package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;

public interface GetBuildingStatisticsUseCase {
    LocalStatistics getStatisticsForBuilding(GameType gameType, BuildingId buildingId);
}