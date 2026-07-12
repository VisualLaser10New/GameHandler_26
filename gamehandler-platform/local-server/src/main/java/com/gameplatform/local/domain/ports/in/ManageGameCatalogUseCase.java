package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

public interface ManageGameCatalogUseCase {
    Game createGame(GameType gameType, String name, BuildingId buildingId);
    Game updateGame(GameId gameId, String newName, GameMachineStatus newStatus);
    void deleteGame(GameId gameId);
}