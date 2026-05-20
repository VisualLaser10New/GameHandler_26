package main.java.com.gameplatform.shared.domain.result;

import main.java.com.gameplatform.shared.domain.model.UserId;
import main.java.com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public record SlotResult(String visitorId, int totalSpins, int creditsIn, int creditsOut, int biggestWin, WinCondition winCondition) implements GameResult{
    @Override
    public UserId getWinnerId() {
        return winCondition == WinCondition.WIN ? new UserId(visitorId) : null;
    }

    @Override
    public List<UserId> getWinnerIds() {
        UserId winner = getWinnerId();
        return winner != null ? List.of(winner) : List.of();
    }

    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
