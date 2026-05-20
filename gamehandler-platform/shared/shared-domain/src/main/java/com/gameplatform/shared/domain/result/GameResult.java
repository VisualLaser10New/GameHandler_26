package main.java.com.gameplatform.shared.domain.result;

import main.java.com.gameplatform.shared.domain.model.UserId;
import main.java.com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public interface GameResult {
    UserId getWinnerId();
    List<UserId> getWinnerIds();
    WinCondition getWinCondition();
}
