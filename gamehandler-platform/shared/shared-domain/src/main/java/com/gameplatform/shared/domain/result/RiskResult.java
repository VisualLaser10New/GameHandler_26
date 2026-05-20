package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

public record RiskResult(UserId winnerId, List<UserId> winnerIds, Map<String, Map<String, Integer>> territoriesAtEnd, int totalRounds, WinCondition winCondition) implements GameResult {
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
