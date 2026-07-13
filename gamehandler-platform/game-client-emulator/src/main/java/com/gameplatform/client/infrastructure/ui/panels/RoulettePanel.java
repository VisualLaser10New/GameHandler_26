package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Emulation panel for Roulette.
 * <p>
 * Each player selects a number (0–36) and an amount to bet, then presses
 * "Piazza Puntata". When all players have bet, the host presses "Gira Ruota"
 * which picks a random number and resolves all bets (win = ×35, lose = 0).
 */
public class RoulettePanel implements GamePanel {

    private final VBox root;
    private final Label statusLabel;
    private final ComboBox<String> playerCombo;
    private final Spinner<Integer> numberSpinner;
    private final Spinner<Integer> amountSpinner;
    private final Button betButton;
    private final Button spinButton;
    private final Label resultLabel;
    private final VBox betsBox;

    private List<String> players = new ArrayList<>();
    private final Map<String, Integer> balances = new LinkedHashMap<>();
    // Map player → (number → amount)
    private final Map<String, Map<String, Integer>> bets = new LinkedHashMap<>();
    private String lastResultText = "";

    public RoulettePanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        Label title = new Label("🎡  ROULETTE");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");

        statusLabel = new Label("Waiting for players...");
        statusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #aaa;");

        // Bet controls
        playerCombo = new ComboBox<>();
        playerCombo.setPromptText("Select player");
        playerCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        playerCombo.setDisable(true);

        numberSpinner = new Spinner<>(0, 36, 7);
        numberSpinner.setEditable(true);
        numberSpinner.setPrefWidth(80);
        numberSpinner.setDisable(true);

        amountSpinner = new Spinner<>(1, 10000, 100, 50);
        amountSpinner.setEditable(true);
        amountSpinner.setPrefWidth(100);
        amountSpinner.setDisable(true);

        betButton = new Button("Place Bet");
        betButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-padding: 8 18;");
        betButton.setDisable(true);
        betButton.setOnAction(e -> placeBet());

        HBox betRow = new HBox(10, playerCombo,
                new Label("N°") {{ setStyle("-fx-text-fill:#ccc;"); }},
                numberSpinner,
                new Label("€") {{ setStyle("-fx-text-fill:#ccc;"); }},
                amountSpinner, betButton);
        betRow.setAlignment(Pos.CENTER);

        spinButton = new Button("🎡  Spin the Wheel!");
        spinButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 16; -fx-font-weight: bold; -fx-padding: 12 32; -fx-background-radius: 8;");
        spinButton.setDisable(true);
        spinButton.setOnAction(e -> spinWheel());

        resultLabel = new Label(" ");
        resultLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #f1c40f;");

        betsBox = new VBox(4);
        betsBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 8; -fx-background-radius: 6;");
        betsBox.setAlignment(Pos.CENTER_LEFT);
        betsBox.setMinWidth(240);

        root.getChildren().addAll(title, statusLabel, betRow, spinButton, resultLabel, new Label("Current bets:") {{ setStyle("-fx-text-fill:#ccc; -fx-font-size:13;"); }}, betsBox);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        balances.clear();
        bets.clear();
        for (String p : participants) {
            balances.put(p, 1000);
            bets.put(p, new LinkedHashMap<>());
        }
        playerCombo.getItems().setAll(participants);
        if (!participants.isEmpty()) playerCombo.setValue(participants.get(0));

        playerCombo.setDisable(false);
        numberSpinner.setDisable(false);
        amountSpinner.setDisable(false);
        betButton.setDisable(false);
        spinButton.setDisable(false);
        statusLabel.setText("Place your bets, then spin the wheel!");
        statusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #2ecc71;");
        resultLabel.setText(" ");
        refreshBetsBox();
    }

    @Override
    public void onGameStopped() {
        playerCombo.setDisable(true);
        numberSpinner.setDisable(true);
        amountSpinner.setDisable(true);
        betButton.setDisable(true);
        spinButton.setDisable(true);
        statusLabel.setText("Session ended");
    }

    public String getWinnerId() {
        return balances.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        balances.forEach((p, b) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(b); });
        return sb.toString();
    }

    private void placeBet() {
        String player = playerCombo.getValue();
        if (player == null) return;
        int number = numberSpinner.getValue();
        int amount = amountSpinner.getValue();
        int balance = balances.getOrDefault(player, 0);
        if (amount > balance) {
            statusLabel.setText(player + " does not have enough balance!");
            statusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #e74c3c;");
            return;
        }
        bets.get(player).merge(String.valueOf(number), amount, Integer::sum);
        balances.put(player, balance - amount);
        refreshBetsBox();
    }

    private void spinWheel() {
        int drawn = new Random().nextInt(37);
        StringBuilder result = new StringBuilder("Number drawn: " + drawn + "\n");
        // Resolve bets
        bets.forEach((player, playerBets) -> {
            int win = playerBets.getOrDefault(String.valueOf(drawn), 0);
            if (win > 0) {
                int payout = win * 35;
                balances.merge(player, payout, Integer::sum);
                result.append(player).append(" WINS ").append(payout).append("€! ");
            }
        });
        bets.forEach((p, b) -> b.clear());
        resultLabel.setText(result.toString());
        resultLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f1c40f;");
        lastResultText = result.toString();
        refreshBetsBox();
    }

    private void refreshBetsBox() {
        betsBox.getChildren().clear();
        balances.forEach((p, b) -> {
            Map<String, Integer> pb = bets.getOrDefault(p, Map.of());
            String betStr = pb.isEmpty() ? "none" : pb.toString();
            Label l = new Label(p + " | Balance: " + b + "€ | Bets: " + betStr);
            l.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12;");
            betsBox.getChildren().add(l);
        });
    }
}
