package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

public interface GameResult {
    UserId getWinnerId();
    List<UserId> getWinnerIds();
    WinCondition getWinCondition();
}
