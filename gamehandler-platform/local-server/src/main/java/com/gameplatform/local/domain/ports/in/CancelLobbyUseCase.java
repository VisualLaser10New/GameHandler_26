package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.UserId;

public interface CancelLobbyUseCase {
    GameSession cancelLobby(GameSessionId sessionId, UserId userId);
}
