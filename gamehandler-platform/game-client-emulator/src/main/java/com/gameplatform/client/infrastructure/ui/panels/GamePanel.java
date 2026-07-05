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
     * Functional interface used by turn-based panels to broadcast a
     * turn change to the other participating emulators via MQTT.
     */
    @FunctionalInterface
    interface TurnPublisher {
        /**
         * @param turnIndex  the new turn index (0-based) into the participants list
         * @param playerName the username of the player whose turn it now is
         */
        void publish(int turnIndex, String playerName);
    }

    /**
     * Functional interface used by board-style panels (currently Chess)
     * to broadcast a piece move to the other participating emulators
     * via MQTT, so all clients show the same board state.
     */
    @FunctionalInterface
    interface MovePublisher {
        /**
         * @param fromRow       source row (0-based)
         * @param fromCol       source column (0-based)
         * @param toRow         target row (0-based)
         * @param toCol         target column (0-based)
         * @param capturedPiece Unicode glyph of the piece on the target
         *                      cell, or {@code null} if empty
         */
        void publish(int fromRow, int fromCol, int toRow, int toCol, String capturedPiece);
    }

    /**
     * Functional interface used by score-based panels (e.g. Darts,
     * Foosball) to broadcast a score snapshot to the other participating
     * emulators via MQTT, so all clients show the same scoreboard.
     */
    @FunctionalInterface
    interface ScorePublisher {
        /**
         * @param scores a full snapshot of player -> score entries
         */
        void publish(java.util.Map<String, Integer> scores);
    }

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

    /**
     * Injects a publisher used by score-based panels (e.g. Darts,
     * Foosball) to broadcast a score snapshot to the other emulators
     * via MQTT, so all clients show the same scoreboard.  May be left
     * as the default no-op by panels that do not have a score.
     *
     * @param scorePublisher publisher for outbound score snapshots
     */
    default void setScorePublisher(ScorePublisher scorePublisher) {
        // no-op by default; score-based panels override this
    }

    /**
     * Called by the parent view when a remote score MQTT message
     * arrives, so the panel can apply the new scores and refresh its
     * UI.  Score-based panels override this; the default
     * implementation is a no-op.
     *
     * @param scores a full snapshot of player -> score entries
     */
    default void onRemoteScore(Map<String, Integer> scores) {
        // no-op by default; score-based panels override this
    }

    /**
     * Injects the context needed by turn-based multiplayer panels
     * (Chess, Risk, Darts, Monopoly) to synchronise turns across
     * emulators. When the local player ends their turn, the panel calls
     * {@code turnPublisher.publish(...)}; the parent view broadcasts an
     * MQTT message and every other emulator receives it via
     * {@link #onRemoteTurnUpdate(int, String)}. Panels that are not
     * turn-based may leave this default no-op implementation.
     *
     * @param turnPublisher publisher used to broadcast turn changes
     * @param currentUser   username of the local player, so the panel
     *                      knows whether it is the active player's turn
     */
    default void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        // no-op by default; turn-based panels override this
    }

    /**
     * Called by the parent view when a remote turn-update MQTT message
     * arrives, so the panel can apply the new turn index, refresh its
     * turn indicator and enable/disable its controls accordingly.
     * Turn-based panels override this; the default implementation is a
     * no-op for panels that do not manage turns.
     *
     * @param turnIndex  the new turn index (0-based) into the participants list
     * @param playerName the username of the player whose turn it now is
     */
    default void onRemoteTurnUpdate(int turnIndex, String playerName) {
        // no-op by default; turn-based panels override this
    }

    /**
     * Injects a publisher used by board-style panels (currently Chess)
     * to broadcast individual piece moves to the other emulators.  May
     * be left as the default no-op by panels that do not have a board.
     *
     * @param movePublisher publisher for outbound moves
     */
    default void setMovePublisher(MovePublisher movePublisher) {
        // no-op by default; board-style panels override this
    }

    /**
     * Called by the parent view when a remote move MQTT message arrives,
     * so the panel can apply the move to its board state and refresh
     * the UI.  Board-style panels override this; the default
     * implementation is a no-op.
     *
     * @param fromRow       source row (0-based)
     * @param fromCol       source column (0-based)
     * @param toRow         target row (0-based)
     * @param toCol         target column (0-based)
     * @param capturedPiece Unicode glyph of the piece captured on the
     *                      target cell, or {@code null} if empty
     */
    default void onRemoteMove(int fromRow, int fromCol, int toRow, int toCol, String capturedPiece) {
        // no-op by default; board-style panels override this
    }
}
