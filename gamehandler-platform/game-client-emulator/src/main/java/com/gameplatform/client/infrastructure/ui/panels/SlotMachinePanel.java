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
 * Pannello di emulazione per la Slot Machine.
 * <p>
 * Il giocatore preme "Spin!" per far girare i tre rulli. Una breve animazione
 * scorre i simboli prima di mostrare il risultato finale. La vincita viene
 * calcolata (100 punti per tre simboli identici, 10 per due, 0 altrimenti)
 * e aggiunta al punteggio corrente del giocatore.
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

    /**
     * Costruisce il pannello della slot machine inizializzando i rulli,
     * l'etichetta del risultato, il punteggio e il pulsante di avvio.
     */
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

        scoreLabel = new Label("Score: 0");
        scoreLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #eee;");

        spinButton = new Button("🎰  SPIN!");
        spinButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: #1a1a1a; -fx-font-size: 20; -fx-font-weight: bold; -fx-padding: 14 40; -fx-background-radius: 10;");
        spinButton.setDisable(true);
        spinButton.setOnAction(e -> doSpin());

        root.getChildren().addAll(title, reels, resultLabel, scoreLabel, spinButton);
    }

    @Override
    public Parent getView() { return root; }

    /**
     * Avvia la partita inizializzando il nome del giocatore (primo partecipante
     * o "player" se la lista &egrave; vuota), azzerando il punteggio e abilitando
     * il pulsante di avvio.
     *
     * @param participants lista dei nomi utente dei partecipanti; viene utilizzato
     *                     solo il primo elemento come nome del giocatore
     */
    @Override
    public void onGameStarted(List<String> participants) {
        playerName = participants.isEmpty() ? "player" : participants.get(0);
        totalScore = 0;
        scoreLabel.setText("Score of " + playerName + ": 0");
        resultLabel.setText(" ");
        reel1.setText("❓");
        reel2.setText("❓");
        reel3.setText("❓");
        spinButton.setDisable(false);
    }

    /**
     * Arresta la partita disabilitando il pulsante di avvio e mostrando
     * il punteggio finale nell'etichetta del risultato.
     */
    @Override
    public void onGameStopped() {
        spinButton.setDisable(true);
        resultLabel.setText("Session ended — Final score: " + totalScore);
        resultLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #f39c12; -fx-font-weight: bold;");
    }

    /**
     * Imposta il callback per la notifica delle variazioni di punteggio
     * alla vista padre.
     *
     * @param scoreConsumer accetta una mappa di nome giocatore {@literal ->} punteggio
     */
    @Override
    public void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        this.scoreConsumer = scoreConsumer;
    }

    /**
     * Trasmette il punteggio corrente alla vista padre tramite il
     * {@code scoreConsumer} se presente.
     */
    private void publishScore() {
        if (scoreConsumer != null) {
            Map<String, Integer> snapshot = new LinkedHashMap<>();
            snapshot.put(playerName, totalScore);
            scoreConsumer.accept(snapshot);
        }
    }

    /**
     * Restituisce il punteggio totale accumulato dal giocatore.
     *
     * @return il punteggio totale corrente
     */
    public int getTotalScore() { return totalScore; }

    /**
     * Restituisce l'identificativo del vincitore della partita.
     * Per la slot machine, il vincitore &egrave; sempre il giocatore locale.
     *
     * @return il nome del giocatore locale
     */
    public String getWinnerId() { return playerName; }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "nomeGiocatore:punteggioTotale".
     *
     * @return stringa con il nome del giocatore e il punteggio totale separati da due punti
     * @see #getWinnerId()
     */
    public String getResultData() { return playerName + ":" + totalScore; }

    /**
     * Avvia l'animazione dei rulli della slot machine.
     * Mostra un'animazione di 10 fotogrammi con simboli casuali, quindi
     * determina il risultato finale e calcola la vincita in base ai simboli
     * mostrati sui tre rulli. Durante l'animazione il pulsante di avvio
     * viene disabilitato.
     */
    private void doSpin() {
        if (spinning) return;
        spinning = true;
        spinButton.setDisable(true);
        resultLabel.setText("Spinning...");
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
            scoreLabel.setText("Score of " + playerName + ": " + totalScore);
            publishScore();

            if (payout >= 100) {
                resultLabel.setText("🎉 JACKPOT! +" + payout + " points!");
                resultLabel.setStyle("-fx-font-size: 18; -fx-text-fill: #f1c40f; -fx-font-weight: bold;");
            } else if (payout > 0) {
                resultLabel.setText("👍 Win! +" + payout + " points");
                resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #2ecc71;");
            } else {
                resultLabel.setText("😔 No win");
                resultLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #e74c3c;");
            }
            spinning = false;
            spinButton.setDisable(false);
        });
        animation.play();
    }

    /**
     * Calcola la vincita in base ai tre simboli mostrati sui rulli.
     *
     * @param r1 simbolo del primo rullo
     * @param r2 simbolo del secondo rullo
     * @param r3 simbolo del terzo rullo
     * @return 100 se tutti e tre i simboli sono identici,
     *         10 se almeno due simboli sono identici,
     *         0 altrimenti
     */
    private int calculatePayout(String r1, String r2, String r3) {
        if (r1.equals(r2) && r2.equals(r3)) return 100;
        if (r1.equals(r2) || r2.equals(r3) || r1.equals(r3)) return 10;
        return 0;
    }

    /**
     * Crea e restituisce un'etichetta per un rullo della slot machine.
     *
     * @return un'etichetta {@link Label} configurata con dimensioni minime
     *         di 80x80 pixel e stile scuro con bordo
     */
    private Label makeReelLabel() {
        Label l = new Label("❓");
        l.setMinSize(80, 80);
        l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-font-size: 42; -fx-background-color: #2a2a2a; -fx-border-color: #555; -fx-border-radius: 8; -fx-background-radius: 8;");
        return l;
    }
}
