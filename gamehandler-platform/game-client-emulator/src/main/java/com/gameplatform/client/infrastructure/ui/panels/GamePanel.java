package com.gameplatform.client.infrastructure.ui.panels;

import javafx.scene.Parent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Common interface for all game-specific emulation panels.
 * <p>
 * Each implementation is responsible for rendering the game-specific
 * controls (e.g. goal buttons for foosball, move controls for chess)
 * and for reacting to lifecycle events (game started / stopped).
 */
public interface GamePanel {

    /**
     * Returns the root JavaFX node for this panel.
     *
     * @return the panel's root {@link Parent}
     */
    Parent getView();

    /**
     * Called when the game session starts.
     *
     * @param participants list of participant usernames in session order
     */
    void onGameStarted(List<String> participants);

    /**
     * Called when the game session stops or is abandoned.
     * Implementations should reset their internal state.
     */
    void onGameStopped();

    /**
     * Injects a callback the panel should invoke whenever a participant's
     * score changes, so the parent view can refresh the shared lateral
     * {@code ScoreboardComponent}. Panels without a numerical score may
     * leave this default no-op implementation.
     *
     * @param scoreConsumer accepts a map of participant/name -> score
     */
    default void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        // no-op by default; panels with a score override this
    }
}
