package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import java.util.List;

public interface ListBuildingGamesUseCase {
    List<Game> getByBuilding(BuildingId buildingId);
}