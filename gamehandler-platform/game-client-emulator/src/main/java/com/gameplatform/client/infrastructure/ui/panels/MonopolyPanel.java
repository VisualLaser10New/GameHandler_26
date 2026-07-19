package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Pannello di emulazione per il Monopoly.
 * <p>
 * Traccia il saldo di denaro di ogni giocatore. I pulsanti consentono di
 * registrare trasferimenti di denaro tra giocatori (es. affitto, acquisto
 * propriet&agrave;) e di avanzare il turno.
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
    private TurnPublisher turnPublisher;
    private String currentUser = "";

    /**
     * Costruisce il pannello del Monopoly inizializzando l'indicatore del turno,
     * la lista dei giocatori, i controlli di trasferimento e il pulsante di fine turno.
     */
    public MonopolyPanel() {
        root = new VBox(14);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20;");

        Label title = new Label("🏦  MONOPOLY");
        title.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        turnLabel = new Label("Waiting...");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #eee;");

        playersBox = new VBox(4);
        playersBox.setAlignment(Pos.CENTER_LEFT);
        playersBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10; -fx-background-radius: 6;");
        playersBox.setMinWidth(240);

        // Transfer controls
        Label txLabel = new Label("Money transfer:");
        txLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13;");

        fromCombo = new ComboBox<>();
        fromCombo.setPromptText("From");
        fromCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        fromCombo.setDisable(true);

        toCombo = new ComboBox<>();
        toCombo.setPromptText("To");
        toCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        toCombo.setDisable(true);

        amountSpinner = new Spinner<>(1, 10000, 200, 100);
        amountSpinner.setEditable(true);
        amountSpinner.setPrefWidth(100);
        amountSpinner.setDisable(true);

        transferButton = new Button("💸 Transfer");
        transferButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 18;");
        transferButton.setDisable(true);
        transferButton.setOnAction(e -> doTransfer());

        HBox txRow = new HBox(10, fromCombo, new Label("→") {{ setStyle("-fx-text-fill:#ccc;"); }}, toCombo,
                new Label("€") {{ setStyle("-fx-text-fill:#ccc;"); }}, amountSpinner, transferButton);
        txRow.setAlignment(Pos.CENTER);

        endTurnButton = new Button("✓ End Turn");
        endTurnButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 10 24; -fx-background-radius: 6;");
        endTurnButton.setDisable(true);
        endTurnButton.setOnAction(e -> endTurn());

        root.getChildren().addAll(title, turnLabel, playersBox, txLabel, txRow, endTurnButton);
    }

    @Override
    public Parent getView() { return root; }

    /**
     * Avvia la partita inizializzando la lista dei partecipanti con un saldo
     * iniziale di 1500 unit&agrave; per ciascuno e popolando i menu a tendina
     * per i trasferimenti.
     *
     * @param participants lista dei nomi utente dei partecipanti in ordine di sessione;
     *                     deve contenere almeno un giocatore per abilitare i trasferimenti
     */
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

        updateTurnLabel();
        applyTurnControls();
        refreshPlayersBox();
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
     * Arresta la partita disabilitando tutti i controlli e aggiornando
     * l'etichetta del turno con il messaggio di fine partita.
     */
    @Override
    public void onGameStopped() {
        fromCombo.setDisable(true);
        toCombo.setDisable(true);
        amountSpinner.setDisable(true);
        transferButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    /**
     * Restituisce l'identificativo del vincitore della partita.
     * Il vincitore &egrave; il giocatore con il saldo pi&ugrave; alto.
     *
     * @return il nome utente del giocatore con il saldo massimo, oppure {@code null}
     *         se non ci sono giocatori o in caso di parit&agrave;
     * @see #getResultData()
     */
    public String getWinnerId() {
        return money.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "giocatore1:saldo1,giocatore2:saldo2".
     *
     * @return stringa con coppie "giocatore:saldo" separate da virgola;
     *         restituisce stringa vuota se non ci sono partecipanti
     * @see #getWinnerId()
     */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        money.forEach((p, m) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(m); });
        return sb.toString();
    }

    /**
     * Esegue un trasferimento di denaro dal giocatore sorgente al giocatore
     * destinazione per l'importo specificato. Il trasferimento viene annullato
     * se il mittente non ha fondi sufficienti, se i giocatori coincidono o se
     * uno dei due non &egrave; selezionato.
     */
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

    /**
     * Termina il turno corrente e passa al giocatore successivo.
     * Aggiorna l'indicatore del turno, lo stato dei controlli e trasmette
     * il cambio di turno agli emulatori remoti. Se la lista dei partecipanti
     * &egrave; vuota, non esegue alcuna operazione.
     */
    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
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
     * Abilita i controlli di trasferimento e fine turno solo quando &egrave;
     * il turno del giocatore locale, mantenendo tutti gli emulatori sincronizzati
     * sul giocatore attivo.
     */
    private void applyTurnControls() {
        boolean myTurn = !players.isEmpty()
                && !currentUser.isBlank()
                && currentUser.equals(players.get(turnIndex));
        endTurnButton.setDisable(!myTurn);
        // Transfer controls: allow the active player to operate.
        fromCombo.setDisable(!myTurn);
        toCombo.setDisable(!myTurn);
        amountSpinner.setDisable(!myTurn);
        transferButton.setDisable(!myTurn);
    }

    /**
     * Aggiorna l'etichetta del turno con il nome del giocatore corrente.
     * Se la lista dei partecipanti &egrave; vuota, non esegue alcuna operazione.
     */
    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turn of: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
    }

    /**
     * Aggiorna la visualizzazione della lista dei giocatori con i saldi correnti.
     * I giocatori con saldo zero o negativo vengono evidenziati in rosso.
     */
    private void refreshPlayersBox() {
        playersBox.getChildren().clear();
        money.forEach((p, m) -> {
            Label l = new Label(p + "  →  " + m + " €");
            l.setStyle("-fx-text-fill: " + (m <= 0 ? "#e74c3c" : "#ddd") + "; -fx-font-size: 13;");
            playersBox.getChildren().add(l);
        });
    }
}
