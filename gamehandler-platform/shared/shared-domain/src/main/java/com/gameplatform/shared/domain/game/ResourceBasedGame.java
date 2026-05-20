package main.java.com.gameplatform.shared.domain.game;

import main.java.com.gameplatform.shared.domain.model.UserId;

import java.util.Map;

public interface ResourceBasedGame {
    Map<UserId, Map<String, Integer>> getResources();
    void updateResource(UserId player, String resourceKey, int newValue);
}
