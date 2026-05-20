package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public record SlotResult(String visitorId, int totalSpins, int creditsIn, int creditsOut, int biggestWin, WinCondition winCondition) implements GameResult{
    @Override
    public UserId getWinnerId() {
        if (winCondition == WinCondition.WIN)
            return new UserId(visitorId);
        else
            return null;
    }

    @Override
    public List<UserId> getWinnerIds() {
        UserId winner = getWinnerId();
        if (winner != null)
            return List.of(winner);
        else
            return List.of();
    }

    @Override
    public WinCondition getWinCondition() {
        return null;
    }
}
