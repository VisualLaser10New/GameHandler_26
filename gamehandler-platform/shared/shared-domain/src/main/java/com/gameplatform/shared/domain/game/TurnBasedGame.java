package main.java.com.gameplatform.shared.domain.game;

import main.java.com.gameplatform.shared.domain.model.UserId;

public interface TurnBasedGame {
    UserId getCurrentPlayer();
    void endTurn();
    int getTurnNumber();
}
