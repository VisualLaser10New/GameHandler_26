package main.java.com.gameplatform.shared.domain.result;

import main.java.com.gameplatform.shared.domain.model.UserId;
import main.java.com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public record ChessResult(UserId winnerId, List<UserId> winnerIds, String terminationReason, String finalFenState, WinCondition winCondition) implements GameResult {
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
