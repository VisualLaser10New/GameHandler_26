package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.game.games.*;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameType;

public class GameFactory {
    public static GameLifecycle createGame(GameType type, GameSessionId sessionId) {
        switch (type) {
            case FOOSBALL:
                return new FoosballGame(sessionId);
            case CHESS:
                return new ChessGame(sessionId);
            case DARTS:
                return new DartsGame(sessionId);
            case MONOPOLY:
                return new MonopolyGame(sessionId);
            case RISK:
                return new RiskGame(sessionId);
            case SLOT_MACHINE:
                return new SlotMachineGame(sessionId);
            case ROULETTE:
                return new RouletteGame(sessionId);
            default:
                throw new IllegalArgumentException("Invalid game type: " + type);
        }
    }
}
