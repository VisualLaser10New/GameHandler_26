package com.gameplatform.client.infrastructure.ui.panels;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Emulation panel for Slot Machine.
 * <p>
 * The player presses "Spin!" to spin the three reels. A brief animation
 * cycles through symbols before showing the final result. The payout is
 * calculated (100 pts for three identical, 10 for two, 0 otherwise) and
 * added to the player's running score.
 */
public class SlotMachinePanel implements GamePanel {

    private static final String[] SYMBOLS = {"🍒", "🍋", "🍊", "🍇", "🔔", "7️⃣", "⭐"};
    private static final Random RANDOM = new Random();

    private final VBox root;
    private final Label reel1;
    private final Label reel2;
    private final Label reel3;
    private final Label resultLabel;
    private final Label scoreLabel;
    private final Button spinButton;

    private String playerName = "";
    private int totalScore = 0;
    private boolean spinning = false;
    private Consumer<Map<String, Integer>> scoreConsumer;

    public SlotMachinePanel() {
        root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 30;");

        Label title = new Label("🎰  SLOT MACHINE");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #f1c40f;");

        reel1 = makeReelLabel();
        reel2 = makeReelLabel();
        reel3 = makeReelLabel();

        HBox reels = new HBox(16, reel1, reel2, reel3);
        reels.setAlignment(Pos.CENTER);

        resultLabel = new Label(" ");
        resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #2ecc71; -fx-font-weight: bold;");

        scoreLabel = new Label("Punteggio: 0");
        scoreLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #eee;");

        spinButton = new Button("🎰  SPIN!");
        spinButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: #1a1a1a; -fx-font-size: 20; -fx-font-weight: bold; -fx-padding: 14 40; -fx-background-radius: 10;");
        spinButton.setDisable(true);
        spinButton.setOnAction(e -> doSpin());

        root.getChildren().addAll(title, reels, resultLabel, scoreLabel, spinButton);
    }

    @Override
    public Parent getView() { return root; }

    @Override
    public void onGameStarted(List<String> participants) {
        playerName = participants.isEmpty() ? "player" : participants.get(0);
        totalScore = 0;
        scoreLabel.setText("Punteggio di " + playerName + ": 0");
        resultLabel.setText(" ");
        reel1.setText("❓");
        reel2.setText("❓");
        reel3.setText("❓");
        spinButton.setDisable(false);
    }

    @Override
    public void onGameStopped() {
        spinButton.setDisable(true);
        resultLabel.setText("Sessione terminata — Punteggio finale: " + totalScore);
        resultLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
    }

    @Override
    public void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        this.scoreConsumer = scoreConsumer;
    }

    private void publishScore() {
        if (scoreConsumer != null) {
            Map<String, Integer> snapshot = new LinkedHashMap<>();
            snapshot.put(playerName, totalScore);
            scoreConsumer.accept(snapshot);
        }
    }

    public int getTotalScore() { return totalScore; }
    public String getWinnerId() { return playerName; }
    public String getResultData() { return playerName + ":" + totalScore; }

    private void doSpin() {
        if (spinning) return;
        spinning = true;
        spinButton.setDisable(true);
        resultLabel.setText("Girando...");
        resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #f39c12;");

        // Brief animation: cycle symbols 10 times before settling
        Timeline animation = new Timeline();
        for (int i = 0; i < 10; i++) {
            final int step = i;
            animation.getKeyFrames().add(new KeyFrame(Duration.millis(80L * (step + 1)), event -> {
                reel1.setText(SYMBOLS[RANDOM.nextInt(SYMBOLS.length)]);
                reel2.setText(SYMBOLS[RANDOM.nextInt(SYMBOLS.length)]);
                reel3.setText(SYMBOLS[RANDOM.nextInt(SYMBOLS.length)]);
            }));
        }
        animation.setOnFinished(event -> {
            String s1 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
            String s2 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
            String s3 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
            reel1.setText(s1);
            reel2.setText(s2);
            reel3.setText(s3);

            int payout = calculatePayout(s1, s2, s3);
            totalScore += payout;
            scoreLabel.setText("Punteggio di " + playerName + ": " + totalScore);
            publishScore();

            if (payout >= 100) {
                resultLabel.setText("🎉 JACKPOT! +" + payout + " punti!");
                resultLabel.setStyle("-fx-font-size: 18; -fx-text-fill: #f1c40f; -fx-font-weight: bold;");
            } else if (payout > 0) {
                resultLabel.setText("👍 Vincita! +" + payout + " punti");
                resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #2ecc71;");
            } else {
                resultLabel.setText("😔 Nessuna vincita");
                resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #e74c3c;");
            }
            spinning = false;
            spinButton.setDisable(false);
        });
        animation.play();
    }

    private int calculatePayout(String r1, String r2, String r3) {
        if (r1.equals(r2) && r2.equals(r3)) return 100;
        if (r1.equals(r2) || r2.equals(r3) || r1.equals(r3)) return 10;
        return 0;
    }

    private Label makeReelLabel() {
        Label l = new Label("❓");
        l.setMinSize(80, 80);
        l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-font-size: 42; -fx-background-color: #2a2a2a; -fx-border-color: #555; -fx-border-radius: 8; -fx-background-radius: 8;");
        return l;
    }
}
