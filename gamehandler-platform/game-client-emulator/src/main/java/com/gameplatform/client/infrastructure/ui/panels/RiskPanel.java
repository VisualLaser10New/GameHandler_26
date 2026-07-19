package com.gameplatform.client.infrastructure.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Pannello di emulazione per il gioco Risiko (Risk).
 * <p>
 * Traccia il numero di armate di ogni giocatore. Il giocatore corrente seleziona
 * un attaccante, un difensore e il numero di dadi da attacco (1&ndash;3).
 * Un tiro di dadi simulato determina le perdite per entrambi gli schieramenti
 * seguendo le regole standard del Risiko.
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

    /**
     * Costruisce il pannello del Risiko inizializzando l'indicatore del turno,
     * la lista delle armate, i controlli di attacco e il pulsante di fine turno.
     */
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
        attackerCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        attackerCombo.setDisable(true);

        defenderCombo = new ComboBox<>();
        defenderCombo.setPromptText("Defender");
        defenderCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        defenderCombo.setDisable(true);

        diceCombo = new ComboBox<>();
        diceCombo.getItems().addAll(1, 2, 3);
        diceCombo.setValue(1);
        diceCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
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

    /**
     * Avvia la partita inizializzando la lista dei partecipanti con un numero
     * di armate iniziale calcolato come {@code 40 - (numeroPartecipanti * 5)}.
     * Popola i menu a tendina per attaccante e difensore.
     *
     * @param participants lista dei nomi utente dei partecipanti in ordine di sessione;
     *                     deve contenere almeno due giocatori per consentire attacchi
     */
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
        attackerCombo.setDisable(true);
        defenderCombo.setDisable(true);
        diceCombo.setDisable(true);
        attackButton.setDisable(true);
        endTurnButton.setDisable(true);
        turnLabel.setText("Match ended");
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
    }

    /**
     * Restituisce l'identificativo del vincitore della partita.
     * Il vincitore &egrave; il giocatore con il maggior numero di armate rimanenti
     * (escludendo i giocatori con zero armate).
     *
     * @return il nome utente del giocatore con il massimo di armate positive,
     *         oppure {@code null} se non ci sono giocatori con armate residue
     * @see #getResultData()
     */
    public String getWinnerId() {
        return armies.entrySet().stream().filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /**
     * Restituisce i dati di risultato della partita nel formato
     * "giocatore1:armate1,giocatore2:armate2".
     *
     * @return stringa con coppie "giocatore:armate" separate da virgola;
     *         restituisce stringa vuota se non ci sono partecipanti
     * @see #getWinnerId()
     */
    public String getResultData() {
        StringBuilder sb = new StringBuilder();
        armies.forEach((p, a) -> { if (sb.length() > 0) sb.append(','); sb.append(p).append(':').append(a); });
        return sb.toString();
    }

    /**
     * Esegue un attacco tra il giocatore attaccante e il difensore selezionati.
     * Simula il tiro dei dadi per entrambi gli schieramenti seguendo le regole
     * standard del Risiko e aggiorna il conteggio delle armate di conseguenza.
     * Se attaccante e difensore coincidono o se uno dei due non &egrave;
     * selezionato, l'attacco viene annullato.
     */
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

    /**
     * Simula il tiro del numero specificato di dadi a sei facce e restituisce
     * i risultati ordinati in ordine decrescente.
     *
     * @param count numero di dadi da tirare (1&ndash;3 per l'attaccante,
     *              1&ndash;2 per il difensore)
     * @return lista dei risultati del tiro ordinata in ordine decrescente
     */
    private List<Integer> rollDice(int count) {
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) rolls.add(rng.nextInt(6) + 1);
        rolls.sort(Comparator.reverseOrder());
        return rolls;
    }

    /**
     * Termina il turno corrente e passa al giocatore successivo.
     * Resetta il messaggio del risultato della battaglia, aggiorna
     * l'indicatore del turno, lo stato dei controlli e trasmette
     * il cambio di turno agli emulatori remoti. Se la lista dei
     * partecipanti &egrave; vuota, non esegue alcuna operazione.
     */
    private void endTurn() {
        if (players.isEmpty()) return;
        turnIndex = (turnIndex + 1) % players.size();
        updateTurnLabel();
        battleResultLabel.setText(" ");
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
     * Abilita i controlli di attacco e fine turno solo quando &egrave; il turno
     * del giocatore locale, in modo che tutti gli emulatori concordino sul
     * giocatore attivo.
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

    /**
     * Aggiorna l'etichetta del turno con il nome del giocatore corrente.
     * Se la lista dei partecipanti &egrave; vuota, non esegue alcuna operazione.
     */
    private void updateTurnLabel() {
        if (players.isEmpty()) return;
        turnLabel.setText("Turn of: " + players.get(turnIndex));
        turnLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
    }

    /**
     * Aggiorna la visualizzazione della lista delle armate con i valori correnti.
     * I giocatori con zero armate vengono evidenziati in rosso.
     */
    private void refreshArmiesBox() {
        armiesBox.getChildren().clear();
        armies.forEach((p, a) -> {
            Label l = new Label(p + "  →  " + a + " armies");
            l.setStyle("-fx-text-fill: " + (a <= 0 ? "#e74c3c" : "#ddd") + "; -fx-font-size: 13;");
            armiesBox.getChildren().add(l);
        });
    }
}
