package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.UserId;

public interface TurnBasedGame {
    UserId getCurrentPlayer();
    void endTurn();
    int getTurnNumber();
}
