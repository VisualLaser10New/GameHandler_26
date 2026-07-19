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
 * Pannello di emulazione per il gioco delle Freccette (Darts).
 * <p>
 * I giocatori, a turno, inseriscono un punteggio per tiro (0&ndash;180).
 * Il pulsante "Registra Tiro" registra il punteggio e "Fine Turno" passa
 * al giocatore successivo. Il tabellone dei punteggi si aggiorna in tempo reale.
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
    private TurnPublisher turnPublisher;
    private ScorePublisher scorePublisher;
    private String currentUser = "";

    /**
     * Costruisce il pannello delle freccette inizializzando l'indicatore del turno,
     * lo spinner del punteggio, i pulsanti di registrazione e fine turno e il tabellone.
     */
    public DartsPanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        turnLabel = new Label("Waiting...");
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Label scoreLabel = new Label("Throw score (0–180):");
        scoreLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13;");

        scoreSpinner = new Spinner<>(0, 180, 0, 1);
        scoreSpinner.setEditable(true);
        scoreSpinner.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        scoreSpinner.setDisable(true);

        recordButton = new Button("🎯 Record Throw");
        recordButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        recordButton.setDisable(true);
        recordButton.setOnAction(e -> recordThrow());

        endTurnButton = new Button("✓ End Turn");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        HBox buttons = new HBox(12, recordButton, endTurnButton);
        buttons.setAlignment(Pos.CENTER);

        Label sbTitle = new Label("Standings:");
        sbTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #eee;");

        scoreboardBox = new VBox(4);
        scoreboardBox.setAlignment(Pos.CENTER);
        scoreboardBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10; -fx-background-radius: 6;");
        scoreboardBox.setMinWidth(200);

        root.getChildren().addAll(turnLabel, scoreLabel, scoreSpinner, buttons, sbTitle, scoreboardBox);
    }

    @Override
    public Parent getView() { return root; }

    /**
     * Avvia la partita inizializzando la lista dei partecipanti e il tabellone
     * dei punteggi con valore iniziale zero per ogni giocatore.
     *
     * @param participants lista dei nomi utente dei partecipanti in ordine di sessione;
     *                     se vuota, non viene registrato alcun punteggio
     */
    @Override
    public void onGameStarted(List<String> participants) {
        this.players = new ArrayList<>(participants);
        this.turnIndex = 0;
        scores.clear();
        for (String p : participants) scores.put(p, 0);

        updateTurnLabel();
        applyTurnControls();
        refreshScoreboard();
    }

    /**
     * Imposta il callback per la notifica delle variazioni di punteggio
     * alla vista padre.
     *
     * @param scoreConsumer accetta una mappa di partecipante/nome {@literal ->} punteggio
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
     * Applica un'istantanea del punteggio ricevuta da un emulatore remoto,
     * sostituendo completamente la mappa locale dei punteggi e aggiornando
     * il tabellone. Se la mappa ricevuta &egrave; {@code null}, i punteggi
     * vengono azzerati.
     *
     * @param remoteScores mappa completa dei punteggi giocatore {@literal ->} punteggio,
     *                     oppure {@code null} per azzerare
     */
    @Override
    public void onRemoteScore(Map<String, Integer> remoteScores) {
        // Apply a score snapshot from a remote player so the local
        // panel and scoreboard stay in sync.  Replace the entire map
        // (the snapshot is authoritative) and refresh the UI.
        scores.clear();
        if (remoteScores != null) {
            scores.putAll(remoteScores);
        }
        refreshScoreboard();
    }

    /**
     * Arresta la partita disabilitando tutti i controlli e aggiornando
     * l'etichetta del turno con il messaggio di fine partita.
     */
    @Override
    public void onGameStopped() {
        scoreSpinner.setDisable(true);
        recordButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    /**
     * Imposta il contesto di turno per la sincronizzazione multiplayer.
     *
     * @param turnPublisher publisher per la trasmissione dei cambi di turno
     * @param currentUser   nome utente del giocatore locale; {@code null} viene
     *                      convertito in stringa vuota
     * @see #onRemoteTurnUpdate(int, String)
     */
    @Override
    public void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        this.turnPublisher = turnPublisher;
        this.currentUser = currentUser != null ? currentUser : "";
        applyTurnControls();
    }

    /**
     * Applica l'aggiornamento del turno ricevuto da un emulatore remoto.
     * Aggiorna l'indice del turno e lo stato dei controlli solo se il nuovo indice
     * &egrave; valido (compreso tra 0 e la dimensione della lista dei partecipanti).
     *
     * @param newTurnIndex il nuovo indice del turno (base 0) nella lista dei partecipanti
     * @param playerName   il nome utente del giocatore a cui spetta il turno
     */
    @Override
    public void onRemoteTurnUpdate(int newTurnIndex, String playerName) {
        if (newTurnIndex >= 0 && newTurnIndex < players.size()) {
            this.turnIndex = newTurnIndex;
            updateTurnLabel();
            applyTurnControls();
        }
    }

    /**
     * Trasmette l'istantanea corrente dei punteggi alla vista padre tramite
     * il {@code scoreConsumer} se presente.
     */
    private void publishScore() {
        if (scoreConsumer != null) {
            scoreConsumer.accept(new LinkedHashMap<>(scores));
        }
    }

    /**
     * Restituisce l'identificativo del vincitore della partita.
     * Il vincitore &egrave; il giocatore con il punteggio pi&ugrave; alto.
     *
     * @return il nome utente del giocatore con il punteggio massimo, oppure {@code null}
     *         in caso di parit&agrave; o se non ci sono giocatori
     * @see #getResultData()
     */
    public String getWinnerId() {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "giocatore1:punteggio1,giocatore2:punteggio2".
     *
     * @return stringa con coppie "giocatore:punteggio" separate da virgola;
     *         restituisce stringa vuota se non ci sono partecipanti
     * @see #getWinnerId()
     */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        scores.forEach((p, s) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(s); });
        return sb.toString();
    }

    /**
     * Registra il punteggio del tiro per il giocatore corrente, sommandolo
     * al suo punteggio totale. Reimposta lo spinner a zero e aggiorna il
     * tabellone. Se la lista dei partecipanti &egrave; vuota, non esegue
     * alcuna operazione.
     */
    private void recordThrow() {
        if (players.isEmpty()) return;
        String current = players.get(turnIndex);
        int val = scoreSpinner.getValue();
        scores.merge(current, val, Integer::sum);
        scoreSpinner.getValueFactory().setValue(0);
        refreshScoreboard();
        broadcastScore();
    }

    /**
     * Trasmette l'istantanea corrente dei punteggi agli emulatori remoti
     * tramite il {@link ScorePublisher} se presente. Invocato dopo la
     * registrazione di un tiro locale.
     */
    private void broadcastScore() {
        if (scorePublisher != null) {
            scorePublisher.publish(new LinkedHashMap<>(scores));
        }
    }

    /**
     * Termina il turno corrente e passa al giocatore successivo.
     * Reimposta lo spinner a zero, aggiorna l'indicatore del turno,
     * lo stato dei controlli e trasmette il cambio di turno agli
     * emulatori remoti. Se la lista dei partecipanti &egrave; vuota,
     * non esegue alcuna operazione.
     */
    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        scoreSpinner.getValueFactory().setValue(0);
        updateTurnLabel();
        applyTurnControls();
        broadcastTurn();
    }

    /**
     * Trasmette il cambio di turno agli emulatori remoti tramite il
     * {@link TurnPublisher} se presente e se la lista dei partecipanti
     * non &egrave; vuota.
     */
    private void broadcastTurn() {
        if (turnPublisher != null && !players.isEmpty()) {
            turnPublisher.publish(turnIndex, players.get(turnIndex));
        }
    }

    /**
     * Abilita i controlli di tiro solo quando &egrave; il turno del giocatore
     * locale, in modo che ogni emulatore rifletta lo stesso giocatore attivo
     * e solo quel client possa registrare tiri o terminare il turno.
     */
    private void applyTurnControls() {
        boolean myTurn = !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
        scoreSpinner.setDisable(!myTurn);
        recordButton.setDisable(!myTurn);
        endTurnButton.setDisable(!myTurn);
    }

    /**
     * Aggiorna l'etichetta del turno con il nome del giocatore corrente.
     * Se la lista dei partecipanti &egrave; vuota, non esegue alcuna operazione.
     */
    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turn of: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    /**
     * Aggiorna il tabellone dei punteggi ordinando i giocatori per punteggio
     * decrescente e notificando la vista padre tramite il {@code scoreConsumer}.
     */
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
