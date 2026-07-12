package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.BuildingId;
import java.util.List;

public interface ListBuildingActiveSessionsUseCase {
    List<GameSession> getActiveSessionsByBuilding(BuildingId buildingId);
}