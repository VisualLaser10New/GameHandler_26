package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Emulation panel for Monopoly.
 * <p>
 * Tracks each player's money balance. Buttons allow recording
 * money transfers between players (e.g. rent, buying properties)
 * and advancing the turn.
 */
public class MonopolyPanel implements GamePanel {

    private final VBox root;
    private final Label turnLabel;
    private final VBox playersBox;
    private final ComboBox<String> fromCombo;
    private final ComboBox<String> toCombo;
    private final Spinner<Integer> amountSpinner;
    private final Button transferButton;
    private final Button endTurnButton;

    private List<String> players = new ArrayList<>();
    private final Map<String, Integer> money = new LinkedHashMap<>();
    private int turnIndex = 0;

    public MonopolyPanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        Label title = new Label("🏦  MONOPOLY");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        turnLabel = new Label("In attesa...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #eee;");

        playersBox = new VBox(4);
        playersBox.setAlignment(Pos.CENTER_LEFT);
        playersBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10; -fx-background-radius: 6;");
        playersBox.setMinWidth(240);

        // Transfer controls
        Label txLabel = new Label("Trasferimento denaro:");
        txLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13;");

        fromCombo = new ComboBox<>();
        fromCombo.setPromptText("Da");
        fromCombo.setStyle("-fx-background-color: #333;");
        fromCombo.setDisable(true);

        toCombo = new ComboBox<>();
        toCombo.setPromptText("A");
        toCombo.setStyle("-fx-background-color: #333;");
        toCombo.setDisable(true);

        amountSpinner = new Spinner<>(1, 10000, 200, 100);
        amountSpinner.setEditable(true);
        amountSpinner.setPrefWidth(100);
        amountSpinner.setDisable(true);

        transferButton = new Button("💸 Trasferisci");
        transferButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 18;");
        transferButton.setDisable(true);
        transferButton.setOnAction(e -> doTransfer());

        HBox txRow = new HBox(10, fromCombo, new Label("→") {{ setStyle("-fx-text-fill:#ccc;"); }}, toCombo,
                new Label("€") {{ setStyle("-fx-text-fill:#ccc;"); }}, amountSpinner, transferButton);
        txRow.setAlignment(Pos.CENTER);

        endTurnButton = new Button("✓ Fine Turno");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        root.getChildren().addAll(title, turnLabel, playersBox, txLabel, txRow, endTurnButton);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        this.turnIndex = 0;
        money.clear();
        for (String p : participants) money.put(p, 1500);

        fromCombo.getItems().setAll(participants);
        toCombo.getItems().setAll(participants);
        fromCombo.setValue(participants.isEmpty() ? null : participants.get(0));
        toCombo.setValue(participants.size() < 2 ? null : participants.get(1));

        fromCombo.setDisable(false);
        toCombo.setDisable(false);
        amountSpinner.setDisable(false);
        transferButton.setDisable(false);
        endTurnButton.setDisable(false);

        updateTurnLabel();
        refreshPlayersBox();
    }

    @Override
    public void onGameStopped() {
        fromCombo.setDisable(true);
        toCombo.setDisable(true);
        amountSpinner.setDisable(true);
        transferButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Partita terminata");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    public String getWinnerId() {
        return money.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        money.forEach((p, m) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(m); });
        return sb.toString();
    }

    private void doTransfer() {
        String from = fromCombo.getValue();
        String to = toCombo.getValue();
        if (from == null || to == null || from.equals(to)) return;
        int amount = amountSpinner.getValue();
        int fromBalance = money.getOrDefault(from, 0);
        if (fromBalance < amount) return;
        money.put(from, fromBalance - amount);
        money.merge(to, amount, Integer::sum);
        refreshPlayersBox();
    }

    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
    }

    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turno di: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
    }

    private void refreshPlayersBox() {
        playersBox.getChildren().clear();
        money.forEach((p, m) -> {
            Label l = new Label(p + "  →  " + m + " €");
            l.setStyle("-fx-text-fill: " + (m <= 0 ? "#e74c3c" : "#ddd") + "; -fx-font-size: 13;");
            playersBox.getChildren().add(l);
        });
    }
}
