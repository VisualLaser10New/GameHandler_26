package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;

public interface PublishGameStatePort {
    void publishState(GameId gameId, GameMachineStatus status);
    void publishSessionEvent(String topic, Object payload);
}
