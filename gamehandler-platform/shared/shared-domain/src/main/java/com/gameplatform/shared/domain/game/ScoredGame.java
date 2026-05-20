package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.UserId;

import java.util.Map;

public interface ScoredGame {
    Map<UserId, Integer> getCurrentScores();
    void recordScore(UserId player, int delta);
}
