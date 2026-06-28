package com.gameplatform.client.infrastructure.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.Map;

/**
 * Reusable JavaFX component that displays a sorted scoreboard of players
 * and their scores.
 * <p>
 * Scores are shown in descending order (highest first). An empty state
 * message is displayed when no scores are available.
 */
public class ScoreboardComponent extends VBox {
    private final Label titleLabel;

    public ScoreboardComponent() {
        setSpacing(4);
        setStyle("-fx-padding: 10; -fx-background-color: #2a2a2a; -fx-border-color: #555; -fx-border-radius: 4;");
        titleLabel = new Label("Scoreboard");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #eee; -fx-font-size: 14;");
        getChildren().add(titleLabel);
    }

    /**
     * Updates the displayed scores, replacing any existing entries.
     *
     * @param scores a map of player name to score; may be {@code null} or empty
     *               to show the "No scores yet" placeholder
     */
    public void updateScores(Map<String, Integer> scores) {
        getChildren().retainAll(titleLabel);

        if (scores == null || scores.isEmpty()) {
            Label empty = new Label("No scores yet");
            empty.setStyle("-fx-text-fill: #999;");
            getChildren().add(empty);
            return;
        }

        scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> {
                    Label scoreLabel = new Label(entry.getKey() + ": " + entry.getValue());
                    scoreLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 13;");
                    getChildren().add(scoreLabel);
                });
    }
}
