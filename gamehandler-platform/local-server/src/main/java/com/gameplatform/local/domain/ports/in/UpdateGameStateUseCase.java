package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;

public interface UpdateGameStateUseCase {
    void updateState(GameId gameId, GameMachineStatus newStatus);
}
