package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameSessionId;

public interface StartLobbyUseCase {
    GameSession startLobby(GameSessionId sessionId);
}
