package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Emulation panel for Risk.
 * <p>
 * Tracks each player's army count. The current player selects an attacker,
 * a defender and the number of attacking dice (1–3). A simulated dice roll
 * determines casualties for both sides following the standard Risk rules.
 */
public class RiskPanel implements GamePanel {

    private final VBox root;
    private final Label turnLabel;
    private final VBox armiesBox;
    private final ComboBox<String> attackerCombo;
    private final ComboBox<String> defenderCombo;
    private final ComboBox<Integer> diceCombo;
    private final Button attackButton;
    private final Button endTurnButton;
    private final Label battleResultLabel;

    private List<String> players = new ArrayList<>();
    private final Map<String, Integer> armies = new LinkedHashMap<>();
    private int turnIndex = 0;
    private final Random rng = new Random();
    private TurnPublisher turnPublisher;
    private String currentUser = "";

    public RiskPanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        Label title = new Label("🗺️  RISK");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #e67e22;");

        turnLabel = new Label("Waiting...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #eee;");

        armiesBox = new VBox(4);
        armiesBox.setAlignment(Pos.CENTER_LEFT);
        armiesBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10; -fx-background-radius: 6;");
        armiesBox.setMinWidth(240);

        // Attack controls
        Label atkLabel = new Label("Attack:");
        atkLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13;");

        attackerCombo = new ComboBox<>();
        attackerCombo.setPromptText("Attacker");
        attackerCombo.setStyle("-fx-background-color: #333;");
        attackerCombo.setDisable(true);

        defenderCombo = new ComboBox<>();
        defenderCombo.setPromptText("Defender");
        defenderCombo.setStyle("-fx-background-color: #333;");
        defenderCombo.setDisable(true);

        diceCombo = new ComboBox<>();
        diceCombo.getItems().addAll(1, 2, 3);
        diceCombo.setValue(1);
        diceCombo.setStyle("-fx-background-color: #333;");
        diceCombo.setDisable(true);

        attackButton = new Button("⚔️ Attack");
        attackButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 8 18;");
        attackButton.setDisable(true);
        attackButton.setOnAction(e -> doAttack());

        HBox atkRow = new HBox(10, attackerCombo, new Label("vs") {{ setStyle("-fx-text-fill:#ccc;"); }},
                defenderCombo, new Label("🎲") {{ setStyle("-fx-text-fill:#ccc;"); }}, diceCombo, attackButton);
        atkRow.setAlignment(Pos.CENTER);

        battleResultLabel = new Label(" ");
        battleResultLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 13;");

        endTurnButton = new Button("✓ End Turn");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        root.getChildren().addAll(title, turnLabel, armiesBox, atkLabel, atkRow, battleResultLabel, endTurnButton);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        this.turnIndex = 0;
        armies.clear();
        for (String p : participants) armies.put(p, 40 - (participants.size() * 5));

        attackerCombo.getItems().setAll(participants);
        defenderCombo.getItems().setAll(participants);
        if (!participants.isEmpty()) attackerCombo.setValue(participants.get(0));
        if (participants.size() >= 2) defenderCombo.setValue(participants.get(1));

        updateTurnLabel();
        applyTurnControls();
        refreshArmiesBox();
    }

    @Override
    public void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        this.turnPublisher = turnPublisher;
        this.currentUser = currentUser != null ? currentUser : "";
        applyTurnControls();
    }

    @Override
    public void onRemoteTurnUpdate(int newTurnIndex, String playerName) {
        if (newTurnIndex >= 0 && newTurnIndex < players.size()) {
            this.turnIndex = newTurnIndex;
            updateTurnLabel();
            applyTurnControls();
        }
    }

    @Override
    public void onGameStopped() {
        attackerCombo.setDisable(true);
        defenderCombo.setDisable(true);
        diceCombo.setDisable(true);
        attackButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    public String getWinnerId() {
        return armies.entrySet().stream().filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        armies.forEach((p, a) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(a); });
        return sb.toString();
    }

    private void doAttack() {
        String attacker = attackerCombo.getValue();
        String defender = defenderCombo.getValue();
        if (attacker == null || defender == null || attacker.equals(defender)) return;

        int atkDice = diceCombo.getValue();
        int defDice = Math.min(2, armies.getOrDefault(defender, 0)); // defender uses up to 2 dice

        // Roll dice
        List<Integer> atkRolls = rollDice(atkDice);
        List<Integer> defRolls = rollDice(defDice);

        // Compare pairs
        int pairs = Math.min(atkRolls.size(), defRolls.size());
        int atkLoss = 0, defLoss = 0;
        for (int i = 0; i < pairs; i++) {
            if (atkRolls.get(i) > defRolls.get(i)) defLoss++;
            else atkLoss++;
        }

        armies.merge(attacker, -atkLoss, Integer::sum);
        armies.merge(defender, -defLoss, Integer::sum);
        // Remove eliminated players
        armies.replaceAll((p, v) -> Math.max(v, 0));

        String result = "⚔️ " + attacker + " " + atkRolls + " vs " + defender + " " + defRolls +
                " → Losses: " + attacker + " -" + atkLoss + ", " + defender + " -" + defLoss;
        battleResultLabel.setText(result);
        refreshArmiesBox();
    }

    private List<Integer> rollDice(int count) {
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) rolls.add(rng.nextInt(6) + 1);
        rolls.sort(Comparator.reverseOrder());
        return rolls;
    }

    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
        battleResultLabel.setText(" ");
        applyTurnControls();
        broadcastTurn();
    }

    private void broadcastTurn() {
        if (turnPublisher != null && !players.isEmpty()) {
            turnPublisher.publish(turnIndex, players.get(turnIndex));
        }
    }

    /**
     * Enables attack / end-turn controls only when it is the local
     * user's turn, so all emulators agree on the active player.
     */
    private void applyTurnControls() {
        boolean myTurn = !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
        endTurnButton.setDisable(!myTurn);
        attackerCombo.setDisable(!myTurn);
        defenderCombo.setDisable(!myTurn);
        diceCombo.setDisable(!myTurn);
        attackButton.setDisable(!myTurn);
    }

    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turn of: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
    }

    private void refreshArmiesBox() {
        armiesBox.getChildren().clear();
        armies.forEach((p, a) -> {
            Label l = new Label(p + "  →  " + a + " armies");
            l.setStyle("-fx-text-fill: " + (a <= 0 ? "#e74c3c" : "#ddd") + "; -fx-font-size: 13;");
            armiesBox.getChildren().add(l);
        });
    }
}
