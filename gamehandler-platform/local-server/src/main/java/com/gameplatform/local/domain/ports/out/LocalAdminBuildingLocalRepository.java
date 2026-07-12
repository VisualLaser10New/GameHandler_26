package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;

public interface LocalAdminBuildingLocalRepository {
    LocalAdminBuilding save(LocalAdminBuilding binding);
    boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId);
    void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId);
    List<LocalAdminBuilding> findByUserId(UserId userId);
}