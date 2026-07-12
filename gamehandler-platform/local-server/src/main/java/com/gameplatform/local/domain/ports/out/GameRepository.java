package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import java.util.List;
import java.util.Optional;

public interface GameRepository {
    Game save(Game game);
    Optional<Game> findById(GameId id);
    Optional<Game> findByIdForUpdate(GameId id);
    List<Game> findByBuildingId(BuildingId buildingId);
    List<Game> findByStatus(GameMachineStatus status);
    List<Game> findAll();
    void deleteById(GameId id);
}
