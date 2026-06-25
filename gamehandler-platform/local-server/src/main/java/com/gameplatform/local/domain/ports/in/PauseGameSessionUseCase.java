package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;

public interface PauseGameSessionUseCase {
    void pause(GameSessionId sessionId);
}
