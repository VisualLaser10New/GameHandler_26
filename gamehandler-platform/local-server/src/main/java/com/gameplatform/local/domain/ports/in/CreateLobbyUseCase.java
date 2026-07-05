package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

public interface CreateLobbyUseCase {
    GameSession createLobby(GameId gameId, GameType gameType, UserId creatorId);
}
