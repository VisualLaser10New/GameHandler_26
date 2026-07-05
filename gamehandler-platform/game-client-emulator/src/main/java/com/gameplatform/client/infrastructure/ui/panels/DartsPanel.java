package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Emulation panel for Darts (Freccette).
 * <p>
 * Players take turns entering a score per throw (0–180).
 * Pressing "Registra Tiro" records the score and "Fine Turno" advances
 * to the next player. The scoreboard updates in real time.
 */
public class DartsPanel implements GamePanel {

    private final VBox root;
    private final Label turnLabel;
    private final Spinner<Integer> scoreSpinner;
    private final Button recordButton;
    private final Button endTurnButton;
    private final VBox scoreboardBox;

    private List<String> players = new ArrayList<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private int turnIndex = 0;
    private Consumer<Map<String, Integer>> scoreConsumer;

    public DartsPanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        turnLabel = new Label("In attesa...");
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Label scoreLabel = new Label("Punteggio tiro (0–180):");
        scoreLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13;");

        scoreSpinner = new Spinner<>(0, 180, 0, 1);
        scoreSpinner.setEditable(true);
        scoreSpinner.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        scoreSpinner.setDisable(true);

        recordButton = new Button("🎯 Registra Tiro");
        recordButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        recordButton.setDisable(true);
        recordButton.setOnAction(e -> recordThrow());

        endTurnButton = new Button("✓ Fine Turno");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        HBox buttons = new HBox(12, recordButton, endTurnButton);
        buttons.setAlignment(Pos.CENTER);

        Label sbTitle = new Label("Classifica:");
        sbTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #eee;");

        scoreboardBox = new VBox(4);
        scoreboardBox.setAlignment(Pos.CENTER);
        scoreboardBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10; -fx-background-radius: 6;");
        scoreboardBox.setMinWidth(200);

        root.getChildren().addAll(turnLabel, scoreLabel, scoreSpinner, buttons, sbTitle, scoreboardBox);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        this.turnIndex = 0;
        scores.clear();
        for (String p : participants) scores.put(p, 0);

        scoreSpinner.setDisable(false);
        recordButton.setDisable(false);
        endTurnButton.setDisable(false);
        updateTurnLabel();
        refreshScoreboard();
    }

    @Override
    public void onGameStopped() {
        scoreSpinner.setDisable(true);
        recordButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Partita terminata");
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    @Override
    public void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        this.scoreConsumer = scoreConsumer;
    }

    private void publishScore() {
        if (scoreConsumer != null) {
            scoreConsumer.accept(new LinkedHashMap<>(scores));
        }
    }

    /** Returns the player with the highest score, or null on tie. */
    public String getWinnerId() {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Returns comma-separated "player:score" pairs. */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        scores.forEach((p, s) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(s); });
        return sb.toString();
    }

    private void recordThrow() {
        if (players.isEmpty()) return;
        String current = players.get(turnIndex);
        int val = scoreSpinner.getValue();
        scores.merge(current, val, Integer::sum);
        scoreSpinner.getValueFactory().setValue(0);
        refreshScoreboard();
    }

    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        scoreSpinner.getValueFactory().setValue(0);
        updateTurnLabel();
    }

    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turno di: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    private void refreshScoreboard() {
        scoreboardBox.getChildren().clear();
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Label l = new Label(e.getKey() + ":  " + e.getValue() + " pt");
                    l.setStyle("-fx-text-fill: #ddd; -fx-font-size: 13;");
                    scoreboardBox.getChildren().add(l);
                });
        publishScore();
    }
}
