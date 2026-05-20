package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.*;

import java.util.List;

public interface GameLifecycle {
    void start(List<UserId> participants);
    void stop(StopReason reason);
    void pause();
    void resume();
    GameStatus getStatus();
    GameType getGameType();
    GameSessionId getSessionId();
}
