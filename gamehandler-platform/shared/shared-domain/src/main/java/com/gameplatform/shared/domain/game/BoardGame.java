package main.java.com.gameplatform.shared.domain.game;

public interface BoardGame {
    String serializeBoardState();
    void restoreBoardState(String serializedState);
}
