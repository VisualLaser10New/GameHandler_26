package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.result.GameResult;

public interface EndGameSessionUseCase {
    void end(GameSessionId sessionId, GameResult result);
}
