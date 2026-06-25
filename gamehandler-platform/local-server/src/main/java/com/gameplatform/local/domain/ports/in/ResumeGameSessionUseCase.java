package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameSessionId;

public interface ResumeGameSessionUseCase {
    void resume(GameSessionId sessionId);
}
