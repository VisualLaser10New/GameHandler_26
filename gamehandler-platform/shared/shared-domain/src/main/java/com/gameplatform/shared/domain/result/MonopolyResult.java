package main.java.com.gameplatform.shared.domain.result;

import main.java.com.gameplatform.shared.domain.model.UserId;
import main.java.com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

public record MonopolyResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalMoney, Map<String, List<String>> ownedProperties, WinCondition winCondition) implements GameResult {
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
