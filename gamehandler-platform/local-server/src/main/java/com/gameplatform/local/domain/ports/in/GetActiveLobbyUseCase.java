package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;

import java.util.Optional;

public interface GetActiveLobbyUseCase {
    Optional<GameSession> getActiveLobby(GameId gameId);
}
