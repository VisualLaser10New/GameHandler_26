package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pannello di emulazione per il Calciobalilla (Foosball).
 * <p>
 * Visualizza due pulsanti goal (uno per squadra) e mantiene il punteggio locale
 * che pu&ograve; essere letto al termine della sessione per costruire il payload
 * del risultato.
 */
public class FoosballPanel implements GamePanel {

    private final VBox root;
    private final Label team1ScoreLabel;
    private final Label team2ScoreLabel;
    private final Label turnLabel;
    private final Button goalTeam1Button;
    private final Button goalTeam2Button;
    private final Button undoTeam1Button;
    private final Button undoTeam2Button;

    private String team1Name = "Team 1";
    private String team2Name = "Team 2";
    private int score1 = 0;
    private int score2 = 0;
    private Consumer<Map<String, Integer>> scoreConsumer;
    private ScorePublisher scorePublisher;

    /**
     * Costruisce il pannello del calciobalilla inizializzando i punteggi,
     * i pulsanti goal e i pulsanti di annullamento.
     */
    public FoosballPanel() {
        root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        turnLabel = new Label("Waiting for players...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #aaa;");

        // Score display
        team1ScoreLabel = new Label("0");
        team1ScoreLabel.setStyle("-fx-font-size: 64; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        team2ScoreLabel = new Label("0");
        team2ScoreLabel.setStyle("-fx-font-size: 64; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");

        Label vsLabel = new Label("VS");
        vsLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #888;");

        HBox scoreBox = new HBox(30, team1ScoreLabel, vsLabel, team2ScoreLabel);
        scoreBox.setAlignment(Pos.CENTER);

        // Goal buttons
        goalTeam1Button = new Button("⚽  GOAL " + team1Name.toUpperCase());
        goalTeam1Button.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 16; -fx-padding: 14 30; -fx-background-radius: 8;");
        goalTeam1Button.setDisable(true);
        goalTeam1Button.setOnAction(e -> recordGoal(1));

        goalTeam2Button = new Button("⚽  GOAL " + team2Name.toUpperCase());
        goalTeam2Button.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 16; -fx-padding: 14 30; -fx-background-radius: 8;");
        goalTeam2Button.setDisable(true);
        goalTeam2Button.setOnAction(e -> recordGoal(2));

        HBox goalButtons = new HBox(20, goalTeam1Button, goalTeam2Button);
        goalButtons.setAlignment(Pos.CENTER);

        // Undo buttons
        undoTeam1Button = new Button("↩ Undo goal " + team1Name);
        undoTeam1Button.setStyle("-fx-background-color: #555; -fx-text-fill: #ddd; -fx-font-size: 12; -fx-padding: 6 16;");
        undoTeam1Button.setDisable(true);
        undoTeam1Button.setOnAction(e -> undoGoal(1));

        undoTeam2Button = new Button("↩ Undo goal " + team2Name);
        undoTeam2Button.setStyle("-fx-background-color: #555; -fx-text-fill: #ddd; -fx-font-size: 12; -fx-padding: 6 16;");
        undoTeam2Button.setDisable(true);
        undoTeam2Button.setOnAction(e -> undoGoal(2));

        HBox undoButtons = new HBox(20, undoTeam1Button, undoTeam2Button);
        undoButtons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(turnLabel, scoreBox, goalButtons, undoButtons);
    }

    @Override
    public Parent getView() {
        return root;
    }

    /**
     * Avvia la partita azzerando i punteggi, assegnando i nomi delle squadre
     * in base ai partecipanti (primo partecipante alla squadra 1, secondo alla
     * squadra 2) e abilitando tutti i controlli.
     *
     * @param participants lista dei nomi utente dei partecipanti; la dimensione
     *                     determina quante squadre vengono assegnate (minimo 1)
     */
    @Override
    public void onGameStarted(List<String> participants) {
        score1 = 0;
        score2 = 0;

        // Assign teams: first half → team1, second half → team2
        if (!participants.isEmpty()) {
            team1Name = participants.get(0);
        }
        if (participants.size() >= 2) {
            team2Name = participants.get(1);
        }

        updateScoreLabels();
        goalTeam1Button.setText("⚽  GOAL " + team1Name.toUpperCase());
        goalTeam2Button.setText("⚽  GOAL " + team2Name.toUpperCase());
        undoTeam1Button.setText("↩ Undo goal " + team1Name);
        undoTeam2Button.setText("↩ Undo goal " + team2Name);

        goalTeam1Button.setDisable(false);
        goalTeam2Button.setDisable(false);
        undoTeam1Button.setDisable(false);
        undoTeam2Button.setDisable(false);
        turnLabel.setText("Match in progress — press the button when a goal is scored");
        turnLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #2ecc71;");
    }

    /**
     * Arresta la partita disabilitando tutti i controlli e mostrando il
     * risultato finale nell'etichetta del turno.
     */
    @Override
    public void onGameStopped() {
        goalTeam1Button.setDisable(true);
        goalTeam2Button.setDisable(true);
        undoTeam1Button.setDisable(true);
        undoTeam2Button.setDisable(true);
        turnLabel.setText("Match ended — " + team1Name + " " + score1 + " : " + score2 + " " + team2Name);
        turnLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #f39c12;");
    }

    /**
     * Imposta il callback per la notifica delle variazioni di punteggio
     * alla vista padre.
     *
     * @param scoreConsumer accetta una mappa di nome squadra {@literal ->} punteggio
     * @see #setScorePublisher(ScorePublisher)
     * @see #onRemoteScore(Map)
     */
    @Override
    public void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        this.scoreConsumer = scoreConsumer;
    }

    /**
     * Imposta il publisher per la trasmissione delle istantanee del punteggio
     * agli emulatori remoti.
     *
     * @param scorePublisher publisher per le istantanee del punteggio in uscita
     * @see #onRemoteScore(Map)
     * @see #setScoreConsumer(Consumer)
     */
    @Override
    public void setScorePublisher(ScorePublisher scorePublisher) {
        this.scorePublisher = scorePublisher;
    }

    /**
     * Applica un'istantanea del punteggio ricevuta da un emulatore remoto.
     * Cerca i punteggi per nome squadra; se il nome non viene trovato,
     * utilizza un fallback posizionale. Se la mappa ricevuta &egrave;
     * {@code null}, non applica alcuna modifica.
     *
     * @param remoteScores mappa completa dei punteggi squadra {@literal ->} punteggio,
     *                     oppure {@code null} per ignorare
     */
    @Override
    public void onRemoteScore(Map<String, Integer> remoteScores) {
        // Apply a score snapshot from a remote client so both emulators
        // show the same score.  Look up by team name to preserve the
        // local name mapping (which may differ from the remote's).
        if (remoteScores != null) {
            Integer s1 = remoteScores.get(team1Name);
            Integer s2 = remoteScores.get(team2Name);
            // Fall back to positional lookup if names differ.
            if (s1 == null && remoteScores.size() >= 1) {
                s1 = remoteScores.values().stream().findFirst().orElse(score1);
            }
            if (s2 == null && remoteScores.size() >= 2) {
                s2 = remoteScores.values().stream().skip(1).findFirst().orElse(score2);
            }
            if (s1 != null) score1 = s1;
            if (s2 != null) score2 = s2;
            updateScoreLabels();
        }
    }

    /**
     * Trasmette l'istantanea corrente dei punteggi alla vista padre tramite
     * il {@code scoreConsumer} se presente.
     */
    private void publishScore() {
        if (scoreConsumer != null) {
            Map<String, Integer> snapshot = new LinkedHashMap<>();
            snapshot.put(team1Name, score1);
            snapshot.put(team2Name, score2);
            scoreConsumer.accept(snapshot);
        }
    }

    /**
     * Trasmette l'istantanea corrente dei punteggi agli emulatori remoti
     * tramite il {@link ScorePublisher} se presente. Invocato dopo un goal
     * o un annullamento locale.
     */
    private void broadcastScore() {
        if (scorePublisher != null) {
            Map<String, Integer> snapshot = new LinkedHashMap<>();
            snapshot.put(team1Name, score1);
            snapshot.put(team2Name, score2);
            scorePublisher.publish(snapshot);
        }
    }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "squadra1:punteggio1,squadra2:punteggio2".
     *
     * @return stringa con le coppie squadra-punteggio separate da virgola
     * @see #getWinnerId()
     */
    public String getResultData() {
        return team1Name + ":" + score1 + "," + team2Name + ":" + score2;
    }

    /**
     * Restituisce il nome della squadra vincente.
     *
     * @return il nome della squadra con il punteggio pi&ugrave; alto, oppure
     *         {@code null} in caso di pareggio
     * @see #getResultData()
     */
    public String getWinnerId() {
        if (score1 > score2) return team1Name;
        if (score2 > score1) return team2Name;
        return null;
    }

    /**
     * Registra un goal per la squadra specificata, incrementando il punteggio
     * e aggiornando le etichette e la trasmissione remota.
     *
     * @param team 1 per la squadra 1, 2 per la squadra 2
     */
    private void recordGoal(int team) {
        if (team == 1) score1++;
        else score2++;
        updateScoreLabels();
        broadcastScore();
    }

    /**
     * Annulla l'ultimo goal registrato per la squadra specificata, decrementando
     * il punteggio solo se &egrave; maggiore di zero.
     *
     * @param team 1 per la squadra 1, 2 per la squadra 2
     */
    private void undoGoal(int team) {
        if (team == 1 && score1 > 0) score1--;
        else if (team == 2 && score2 > 0) score2--;
        updateScoreLabels();
        broadcastScore();
    }

    /**
     * Aggiorna le etichette dei punteggi con i valori correnti e notifica
     * la vista padre tramite il {@code scoreConsumer}.
     */
    private void updateScoreLabels() {
        team1ScoreLabel.setText(String.valueOf(score1));
        team2ScoreLabel.setText(String.valueOf(score2));
        publishScore();
    }
}
