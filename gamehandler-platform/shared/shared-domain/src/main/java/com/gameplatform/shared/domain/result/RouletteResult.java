package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public record RouletteResult(String visitorId, int totalRounds, int totalBetAmount, int totalPayout, List<String> winningNumbers, WinCondition winCondition) implements GameResult {
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
