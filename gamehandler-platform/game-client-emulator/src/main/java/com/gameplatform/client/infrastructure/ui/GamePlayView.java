package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.GameOrchestrationService;
import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import com.gameplatform.client.infrastructure.mqtt.StateSubscriber;
import com.gameplatform.client.infrastructure.ui.components.ScoreboardComponent;
import com.gameplatform.client.infrastructure.ui.components.TimerComponent;
import com.gameplatform.client.infrastructure.ui.panels.*;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.MovePayload;
import com.gameplatform.shared.mqtt.payload.ScorePayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionResumePayload;
import com.gameplatform.shared.mqtt.payload.TurnPayload;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Vista JavaFX per la sessione di gioco attiva.
 * <p>
 * Mostra il nome e il tipo del gioco nella parte superiore, un
 * {@link ScoreboardComponent} e un {@link TimerComponent} a sinistra,
 * un pannello di emulazione specifico per il gioco al centro e pulsanti
 * di controllo del ciclo di vita (Start, Pause, Resume, Stop) in basso.
 * La gestione della sessione è delegata a {@link GameOrchestrationService}
 * tramite MQTT; il pannello UI specifico del gioco viene costruito da
 * {@link #buildGamePanel()} all'avvio della sessione.
 */
public class GamePlayView {

    private final BorderPane root;
    private final ScoreboardComponent scoreboard;
    private final TimerComponent timer;
    private final Label gameInfoLabel;
    private final Label statusLabel;
    private final VBox controlsArea;
    private final Button startButton;
    private final Button pauseButton;
    private final Button stopButton;
    private final Button resumeButton;
    private final Button backToHomeButton;

    private GameOrchestrationService orchestrationService;
    private SessionPublisher sessionPublisher;
    private MqttClientAdapter mqttAdapter;
    private String buildingId;
    private GameStateDto currentGameState;
    private String currentUsername = "player";
    private String currentUserId;
    private String gameId = "game-1";
    private GamePanel activePanel;

    // Per-game turn subscription: kept so we can unsubscribe when the
    // session ends, avoiding leaks across matches on different game machines.
    private String currentTurnTopic;
    private StateSubscriber turnSubscriber;

    // Guards against double-processing: once the game has ended (locally
    // or via a remote end event), stopGame() and onRemoteGameEnded()
    // become no-ops so we don't re-publish session/end or double-clear.
    private boolean gameEnded;

    // Idempotency guard for multiplayer pause/resume sync: flipped together
    // with the button state in setGameRunningState()/setGamePausedState() and
    // checked in applyLocalPause()/applyLocalResume() so repeated MQTT echoes
    // or QoS-1 redeliveries (including the server echo that carries
    // pausedBy=null) do not re-trigger the transition or overwrite a richer
    // "Match paused by X" status.
    private boolean localPaused;

    // Lobby-provided participants (set when coming from LobbyView)
    private List<String> lobbyParticipants;
    private String lobbySessionId;

    // Callback to navigate back to game selection after the match ends
    private Runnable onBackToHome;

    /**
     * Costruisce la vista di gioco.
     * <p>
     * Inizializza il layout con le aree per le informazioni di gioco,
     * il punteggio, il timer, il pannello di gioco centrale e i pulsanti
     * di controllo. All'avvio imposta lo stato di attesa nella lobby.
     */
    public GamePlayView() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a1a;");

        gameInfoLabel = new Label("No game selected");
        gameInfoLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #eee; -fx-padding: 10 14;");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12; -fx-padding: 0 14 4 14;");

        VBox topBox = new VBox(0, gameInfoLabel, statusLabel);
        topBox.setStyle("-fx-background-color: #151515; -fx-border-color: #333; -fx-border-width: 0 0 1 0;");

        scoreboard = new ScoreboardComponent();
        timer = new TimerComponent();

        VBox leftPanel = new VBox(10, scoreboard, timer);
        leftPanel.setStyle("-fx-padding: 12;");

        controlsArea = new VBox(12);
        controlsArea.setAlignment(Pos.CENTER);
        controlsArea.setStyle("-fx-padding: 10;");

        startButton = createButton("▶  Start Match", "#27ae60");
        pauseButton = createButton("⏸  Pause", "#f39c12");
        stopButton  = createButton("⏹  End", "#e74c3c");
        resumeButton = createButton("▶  Resume", "#3498db");
        backToHomeButton = createButton("←  Back to home", "#7f8c8d");

        startButton.setOnAction(e -> startGame());
        pauseButton.setOnAction(e -> pauseGame());
        stopButton.setOnAction(e -> stopGame());
        resumeButton.setOnAction(e -> resumeGame());
        backToHomeButton.setOnAction(e -> { if (onBackToHome != null) onBackToHome.run(); });

        HBox buttonBar = new HBox(10, startButton, pauseButton, resumeButton, stopButton, backToHomeButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setStyle("-fx-padding: 10; -fx-background-color: #151515; -fx-border-color: #333; -fx-border-width: 1 0 0 0;");

        root.setTop(topBox);
        root.setLeft(leftPanel);
        root.setBottom(buttonBar);

        setInLobbyState();
    }

    // ─────────────────────────── Public API ───────────────────────────────────

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Avvolge il contenuto in uno {@link ScrollPane} con adattamento a larghezza
     * e altezza, senza barra di scorrimento orizzontale.
     * <p>
     * La barra verticale appare solo quando il contenuto supera l'altezza
     * del viewport. Impedisce che la barra dei pulsanti venga spinta fuori
     * dalla finestra grazie al layout {@link BorderPane}.
     *
     * @param content il contenuto da avvolgere; non null
     * @return uno {@link ScrollPane} contenente il contenuto
     */
    private ScrollPane wrapScroll(Parent content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(false);
        scroll.setStyle("-fx-background: #1a1a1a; -fx-background-color: #1a1a1a;");
        return scroll;
    }

    /**
     * Imposta il contenuto nella regione centrale della vista.
     * <p>
     * Avvolge il contenuto in uno {@link ScrollPane} per garantire che
     * la barra dei pulsanti inferiore rimanga sempre visibile.
     *
     * @param content il contenuto da posizionare al centro; non null
     */
    private void setCenterContent(Parent content) {
        root.setCenter(wrapScroll(content));
    }

    /**
     * Inietta il servizio di orchestrazione del gioco.
     * <p>
     * Deve essere chiamato prima di mostrare questa vista.
     *
     * @param service il servizio di orchestrazione; non null
     */
    public void setOrchestrationService(GameOrchestrationService service) {
        this.orchestrationService = service;
    }

    /**
     * Inietta il publisher di sessione per i topic MQTT pausa/ripresa.
     *
     * @param publisher il publisher di sessione; può essere null in
     *                  modalità solo locale
     */
    public void setSessionPublisher(SessionPublisher publisher) {
        this.sessionPublisher = publisher;
    }

    /**
     * Imposta il nome utente dell'utente autenticato.
     * <p>
     * Utilizzato come identificativo del partecipante nelle partite
     * e per la visualizzazione nei pannelli di gioco.
     *
     * @param username il nome utente; se null o vuoto viene ignorato
     */
    public void setCurrentUser(String username) {
        if (username != null && !username.isBlank()) this.currentUsername = username;
    }

    /**
     * Imposta l'identificativo stabile dell'utente autenticato.
     * <p>
     * Utilizzato come identità lato server per le partite single-player,
     * in modo che le statistiche e i match fact siano associati all'ID
     * utente. Il nome utente rimane per la visualizzazione nei pannelli.
     * Può essere null per mantenere il comportamento storico basato sul nome utente.
     *
     * @param userId l'UUID dell'utente; può essere null
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    /**
     * Imposta l'identificativo della macchina da gioco.
     *
     * @param id l'ID della macchina da gioco; se null o vuoto viene ignorato
     */
    public void setGameId(String id) {
        if (id != null && !id.isBlank()) this.gameId = id;
    }

    /**
     * Inietta l'adattatore MQTT e l'identificativo dell'edificio.
     * <p>
     * Utilizzati per sottoscrivere i topic di aggiornamento turno durante
     * le partite multiplayer. Entrambi possono essere null in modalità
     * solo locale, disabilitando la sincronizzazione dei turni.
     *
     * @param adapter    l'adattatore MQTT; può essere null
     * @param buildingId l'identificativo dell'edificio; può essere null
     */
    public void setMqttContext(MqttClientAdapter adapter, String buildingId) {
        this.mqttAdapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Registra il callback per tornare alla selezione del gioco dopo la partita.
     *
     * @param callback l'azione da eseguire per tornare alla home; può essere null
     */
    public void setOnBackToHome(Runnable callback) { this.onBackToHome = callback; }

    /**
     * Configura la vista per una specifica macchina da gioco.
     * <p>
     * Resetta lo stato della partita, i partecipanti della lobby e
     * l'indicatore di fine partita, quindi imposta lo stato di attesa.
     *
     * @param state il DTO dello stato del gioco selezionato; non null
     */
    public void setGameState(GameStateDto state) {
        this.currentGameState = state;
        this.lobbyParticipants = null;
        this.lobbySessionId = null;
        this.gameEnded = false;
        gameInfoLabel.setText(state.name() + "  [" + state.gameType() + "]");
        statusLabel.setText("Ready to start the match");
        setInLobbyState();
    }

    /**
     * Configura la vista quando si entra da una sessione lobby già avviata.
     * <p>
     * Imposta lo stato della partita, l'ID di sessione e i partecipanti,
     * quindi avvia il pannello di gioco e il timer.
     *
     * @param state        il DTO dello stato del gioco; non null
     * @param sessionId    l'ID di sessione assegnato dal server; non null
     * @param participants la lista dei partecipanti confermati; non null
     */
    public void setFromLobby(GameStateDto state, String sessionId, List<String> participants) {
        configureActiveSession(state, sessionId, participants, "Lobby started — match in progress");
    }

    /**
     * Configura la vista quando si entra da una partita torneo già avviata.
     * <p>
     * Segue la stessa logica di {@link #setFromLobby} ma con un messaggio
     * di stato appropriato per il torneo. L'avvio della partita avviene
     * in modo sincrono via REST e salta la fase lobby.
     *
     * @param state        il DTO dello stato del gioco sintetizzato dalla sessione; non null
     * @param sessionId    l'ID di sessione assegnato dal server; non null
     * @param participants la lista dei partecipanti confermati (tipicamente
     *                     participantA e participantB della partita torneo); non null
     */
    public void setFromTournamentMatch(GameStateDto state, String sessionId, List<String> participants) {
        configureActiveSession(state, sessionId, participants, "Tournament match in progress");
    }

    /**
     * Configura internamente una sessione attiva con i parametri forniti.
     * <p>
     * Imposta lo stato del gioco, l'ID di sessione e i partecipanti,
     * costruisce il pannello di gioco, avvia il timer e imposta lo
     * stato di esecuzione.
     *
     * @param state          il DTO dello stato del gioco; non null
     * @param sessionId      l'ID di sessione; può essere null
     * @param participants   la lista dei partecipanti; non null
     * @param statusMessage  il messaggio di stato da visualizzare; non null
     */
    private void configureActiveSession(GameStateDto state, String sessionId,
                                       List<String> participants, String statusMessage) {
        this.currentGameState = state;
        this.lobbySessionId = sessionId;
        this.lobbyParticipants = participants;
        this.gameEnded = false;
        gameInfoLabel.setText(state.name() + "  [" + state.gameType() + "]");
        statusLabel.setText(statusMessage);
        buildGamePanel();
        timer.startTimer();
        setGameRunningState();
    }

    // ─────────────────────────── Button handlers ──────────────────────────────

    /**
     * Avvia la partita sul server o in modalità locale.
     * <p>
     * Utilizza i partecipanti della lobby se disponibili, altrimenti
     * il singolo utente corrente. Se il servizio di orchestrazione è
     * presente, avvia la partita su un thread separato; altrimenti
     * avvia in modalità solo locale.
     */
    private void startGame() {
        if (currentGameState == null) return;
        setStatus("Starting...");
        startButton.setDisable(true);

        List<String> participants = lobbyParticipants != null
                ? lobbyParticipants
                : List.of(currentUserId != null ? currentUserId : currentUsername);

        if (orchestrationService != null) {
            // Real server-backed start
            new Thread(() -> {
                try {
                    orchestrationService.startGame(currentGameState.gameType(), participants.stream()
                            .map(p -> p)
                            .toList());
                    Platform.runLater(() -> {
                        buildGamePanel();
                        timer.startTimer();
                        setGameRunningState();
                        setStatus("Match in progress");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showError("Unable to start the match: " + ex.getMessage());
                        startButton.setDisable(false);
                        setStatus("Start error");
                    });
                }
            }, "start-game-thread").start();
        } else {
            // Fallback: local-only mode (no server wired)
            buildGamePanel();
            timer.startTimer();
            setGameRunningState();
            setStatus("Match in progress (local)");
        }
    }

    /**
     * Mette in pausa la partita in corso.
     * <p>
     * Pubblica un evento di pausa sul topic MQTT {@code session/pause}
     * e notifica il servizio di orchestrazione. Applica localmente
     * l'effetto di pausa (timer, pulsanti, messaggio di stato).
     */
    private void pauseGame() {
        String effectiveGameId = currentGameState != null ? currentGameState.gameId() : gameId;
        String effectiveSessionId = lobbySessionId;
        if (effectiveSessionId == null && orchestrationService != null
                && orchestrationService.getCurrentSessionId() != null) {
            effectiveSessionId = orchestrationService.getCurrentSessionId().value();
        }
        if (sessionPublisher != null && effectiveSessionId != null) {
            sessionPublisher.publishPause(effectiveGameId, effectiveSessionId, currentUsername);
        }
        if (orchestrationService != null && orchestrationService.isGameInProgress()) {
            orchestrationService.pauseGame();
        }
        applyLocalPause(currentUsername);
    }

    /**
     * Riprende la partita dopo una pausa.
     * <p>
     * Pubblica un evento di ripresa sul topic MQTT {@code session/resume}
     * e notifica il servizio di orchestrazione. Applica localmente
     * l'effetto di ripresa (timer, pulsanti, messaggio di stato).
     */
    private void resumeGame() {
        String effectiveGameId = currentGameState != null ? currentGameState.gameId() : gameId;
        String effectiveSessionId = lobbySessionId;
        if (effectiveSessionId == null && orchestrationService != null
                && orchestrationService.getCurrentSessionId() != null) {
            effectiveSessionId = orchestrationService.getCurrentSessionId().value();
        }
        if (sessionPublisher != null && effectiveSessionId != null) {
            sessionPublisher.publishResume(effectiveGameId, effectiveSessionId);
        }
        if (orchestrationService != null && orchestrationService.isGameInProgress()) {
            orchestrationService.resumeGame();
        }
        applyLocalResume();
    }

    /**
     * Applica localmente l'effetto di pausa senza pubblicare eventi MQTT.
     * <p>
     * Ferma il timer, aggiorna i pulsanti e imposta il messaggio di stato.
     * Metodo idempotente: la prima invocazione imposta {@code localPaused}
     * a true; invocazioni successive (echo MQTT o ridelivery QoS-1) non
     * producono effetti, preservando lo stato originale.
     *
     * @param pausedBy il nome di chi ha messo in pausa; può essere null
     */
    private void applyLocalPause(String pausedBy) {
        if (localPaused) return;
        timer.stopTimer();
        setGamePausedState();
        setStatus("Match paused" + (pausedBy != null && !pausedBy.isBlank() ? " by " + pausedBy : ""));
    }

    /**
     * Applica localmente l'effetto di ripresa senza pubblicare eventi MQTT.
     * <p>
     * Riprende il timer, aggiorna i pulsanti e imposta il messaggio di stato.
     * Metodo idempotente: non produce effetti se la partita non era
     * localmente in pausa.
     */
    private void applyLocalResume() {
        if (!localPaused) return;
        timer.resumeTimer();
        setGameRunningState();
        setStatus("Match in progress");
    }

    private void stopGame() {
        if (gameEnded) return;
        gameEnded = true;

        // Extract result data from the active panel before stopping
        String winnerId = null;
        WinCondition winCondition = WinCondition.DRAW;
        String resultData = null;

        if (activePanel instanceof FoosballPanel fp) {
            winnerId = fp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = fp.getResultData();
        } else if (activePanel instanceof ChessPanel cp) {
            winnerId = cp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = cp.getResultData();
        } else if (activePanel instanceof DartsPanel dp) {
            winnerId = dp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = dp.getResultData();
        } else if (activePanel instanceof SlotMachinePanel sp) {
            winnerId = sp.getWinnerId();
            winCondition = WinCondition.WIN;
            resultData = sp.getResultData();
        } else if (activePanel instanceof RoulettePanel rp) {
            winnerId = rp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = rp.getResultData();
        } else if (activePanel instanceof MonopolyPanel mp) {
            winnerId = mp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = mp.getResultData();
        } else if (activePanel instanceof RiskPanel rkp) {
            winnerId = rkp.getWinnerId();
            winCondition = winnerId != null ? WinCondition.WIN : WinCondition.DRAW;
            resultData = rkp.getResultData();
        }

        if (activePanel != null) activePanel.onGameStopped();

        // Single-player games (min == max == 1): the panel renders the local
        // user's USERNAME as the winner; publish the user's stable id (UUID)
        // instead so the server records the participant / winner on the user
        // id, matching /api/players/me/statistics. Multiplayer keeps the
        // panel's reported identity (turn-sync echo contract — see report).
        if (currentGameState != null && currentGameState.maxPlayers() == 1
                && currentUserId != null && winnerId != null) {
            winnerId = currentUserId;
        }

        // Publish session/end to the server so it can release the game machine.
        // We must use the gameId from currentGameState (e.g. "game-slot-1"),
        // not the local GAME_ID env var, and the sessionId from the lobby or
        // orchestrationService — whichever is available.
        String effectiveGameId = currentGameState != null ? currentGameState.gameId() : gameId;
        String effectiveSessionId = lobbySessionId;
        if (effectiveSessionId == null && orchestrationService != null
                && orchestrationService.getCurrentSessionId() != null) {
            effectiveSessionId = orchestrationService.getCurrentSessionId().value();
        }

        if (sessionPublisher != null && effectiveSessionId != null) {
            sessionPublisher.publishEnd(effectiveGameId, effectiveSessionId, winnerId, winCondition, resultData);
        }
        if (orchestrationService != null && orchestrationService.isGameInProgress()) {
            orchestrationService.stopGame(StopReason.COMPLETED, winnerId, winCondition, resultData);
        }

        // Clear lobby state so a future match (or navigation back) doesn't
        // accidentally reuse the old sessionId/participants.
        lobbySessionId = null;
        lobbyParticipants = null;

        unsubscribeTurnTopic();
        timer.stopTimer();
        setCenterContent(controlsArea);
        activePanel = null;
        scoreboard.updateScores(null);
        setGameEndedState();
        setStatus("Match ended");
    }

    /**
     * Gestisce la conclusione remota della partita ricevuta via MQTT.
     * <p>
     * Termina l'interfaccia utente locale senza ripubblicare l'evento
     * di fine partita, evitando echo sul server. Entrambi gli emulatori
     * terminano nello stato "partita conclusa". Il servizio di
     * orchestrazione viene forzatamente resettato per consentire una
     * nuova partita.
     *
     * @param endPayload il payload MQTT di fine sessione; può essere null
     */
    private void onRemoteGameEnded(
            com.gameplatform.shared.mqtt.payload.SessionEndPayload endPayload) {
        if (gameEnded) return;
        gameEnded = true;

        if (activePanel != null) activePanel.onGameStopped();

        // Clear orchestration state WITHOUT publishing end (the remote
        // player already did; re-publishing would echo on the server).
        if (orchestrationService != null) {
            orchestrationService.forceClear();
        }

        lobbySessionId = null;
        lobbyParticipants = null;

        unsubscribeTurnTopic();
        timer.stopTimer();
        setCenterContent(controlsArea);
        activePanel = null;
        scoreboard.updateScores(null);
        setGameEndedState();

        String winner = endPayload != null && endPayload.winnerId() != null
                ? endPayload.winnerId() : "opponent";
        setStatus("Match ended by the opponent (" + winner + ")");
    }

    // ─────────────────────────── Helpers ──────────────────────────────────────

    /**
     * Costruisce e mostra il pannello di emulazione specifico per il gioco.
     * <p>
     * Seleziona il pannello appropriato in base al tipo di gioco corrente,
     * avvia la partita con i partecipanti, collega il consumer del punteggio
     * e attiva la sincronizzazione dei turni via MQTT.
     */
    private void buildGamePanel() {
        if (currentGameState == null) return;

        List<String> participants = lobbyParticipants != null
                ? lobbyParticipants
                : List.of(currentUsername);

        activePanel = switch (currentGameState.gameType()) {
            case FOOSBALL     -> new FoosballPanel();
            case CHESS        -> new ChessPanel();
            case DARTS        -> new DartsPanel();
            case SLOT_MACHINE -> new SlotMachinePanel();
            case ROULETTE     -> new RoulettePanel();
            case MONOPOLY     -> new MonopolyPanel();
            case RISK         -> new RiskPanel();
        };

        activePanel.onGameStarted(participants);
        activePanel.setScoreConsumer(scoreboard::updateScores);
        wireTurnSynchronization();
        setCenterContent(activePanel.getView());

        java.util.Map<String, Integer> scores = new java.util.LinkedHashMap<>();
        participants.forEach(p -> scores.put(p, 0));
        scoreboard.updateScores(scores);
    }

    /**
     * Collega la sincronizzazione multiplayer basata sui turni per la partita corrente.
     * <p>
     * Inietta un {@link com.gameplatform.client.infrastructure.ui.panels.GamePanel.TurnPublisher}
     * nel pannello attivo per la trasmissione dei turni e sottoscrive il topic MQTT
     * {@code session/turn} per ricevere aggiornamenti remoti. I pannelli single-player
     * o non basati su turni ignorano entrambi tramite le implementazioni predefinite
     * in {@link GamePanel}. Collega anche i publisher per le mosse e i punteggi.
     */
    private void wireTurnSynchronization() {
        unsubscribeTurnTopic();

        String effectiveGameId = currentGameState != null ? currentGameState.gameId() : gameId;
        String effectiveSessionId = lobbySessionId;
        if (effectiveSessionId == null && orchestrationService != null
                && orchestrationService.getCurrentSessionId() != null) {
            effectiveSessionId = orchestrationService.getCurrentSessionId().value();
        }
        final String sessionId = effectiveSessionId;

        activePanel.setTurnContext(
                (turnIndex, playerName) -> {
                    if (sessionPublisher != null && sessionId != null) {
                        sessionPublisher.publishTurn(effectiveGameId, sessionId, turnIndex, playerName);
                    }
                },
                currentUsername);

        // Inject the move publisher so board-style panels (Chess) can
        // broadcast individual piece moves to the other emulators.
        activePanel.setMovePublisher(
                (fromRow, fromCol, toRow, toCol, capturedPiece) -> {
                    if (sessionPublisher != null && sessionId != null) {
                        sessionPublisher.publishMove(effectiveGameId, sessionId,
                                fromRow, fromCol, toRow, toCol, capturedPiece);
                    }
                });

        // Inject the score publisher so score-based panels (Darts,
        // Foosball) can broadcast score snapshots to the other emulators.
        activePanel.setScorePublisher(
                scores -> {
                    if (sessionPublisher != null && sessionId != null) {
                        sessionPublisher.publishScore(effectiveGameId, sessionId, scores);
                    }
                });

        if (mqttAdapter != null && mqttAdapter.isConnected() && buildingId != null) {
            currentTurnTopic = "building/" + buildingId + "/game/" + effectiveGameId + "/session/+";
            try {
                turnSubscriber = new StateSubscriber(mqttAdapter, buildingId, (topic, payload) -> {
                    String[] tokens = topic.split("/");
                    if (tokens.length < 6) return;
                    String action = tokens[5];
                    try {
                        if ("turn".equals(action)) {
                            TurnPayload turn = MqttPayloadSerializer.deserialize(payload, TurnPayload.class);
                            if (activePanel != null) {
                                Platform.runLater(() -> activePanel.onRemoteTurnUpdate(turn.turnIndex(), turn.playerName()));
                            }
                        } else if ("move".equals(action)) {
                            // A remote player moved a piece. Apply it to
                            // the local board so both emulators show the
                            // same state.
                            MovePayload move = MqttPayloadSerializer.deserialize(payload, MovePayload.class);
                            if (activePanel != null) {
                                Platform.runLater(() -> activePanel.onRemoteMove(
                                        move.fromRow(), move.fromCol(),
                                        move.toRow(), move.toCol(),
                                        move.capturedPiece()));
                            }
                        } else if ("score".equals(action)) {
                            // A remote player's score changed. Apply the
                            // snapshot so both emulators show the same
                            // scoreboard.
                            ScorePayload scoreMsg = MqttPayloadSerializer.deserialize(payload, ScorePayload.class);
                            if (activePanel != null && scoreMsg.scores() != null) {
                                Platform.runLater(() -> activePanel.onRemoteScore(scoreMsg.scores()));
                            }
                        } else if ("end".equals(action)) {
                            // The remote player (or the server) ended the
                            // session. Terminate the match on this client
                            // without re-publishing the end event.
                            com.gameplatform.shared.mqtt.payload.SessionEndPayload endPayload =
                                    MqttPayloadSerializer.deserialize(payload, com.gameplatform.shared.mqtt.payload.SessionEndPayload.class);
                            Platform.runLater(() -> onRemoteGameEnded(endPayload));
                        } else if ("pause".equals(action)) {
                            // A remote peer (or the server echo) paused the
                            // session. Apply locally without re-publishing so
                            // every emulator's timer stops; applyLocalPause is
                            // idempotent so echoes/redeliveries are skipped.
                            SessionPausePayload pauseMsg =
                                    MqttPayloadSerializer.deserialize(payload, SessionPausePayload.class);
                            Platform.runLater(() -> applyLocalPause(pauseMsg.pausedBy()));
                        } else if ("resume".equals(action)) {
                            // A remote peer (or the server echo) resumed the
                            // session. Apply locally without re-publishing so
                            // every emulator's timer resumes; applyLocalResume is
                            // idempotent.
                            SessionResumePayload resumeMsg =
                                    MqttPayloadSerializer.deserialize(payload, SessionResumePayload.class);
                            Platform.runLater(() -> applyLocalResume());
                        }
                    } catch (Exception e) {
                        // Ignore malformed payloads
                    }
                });
                turnSubscriber.subscribeToSessionEvents(effectiveGameId);
            } catch (Exception e) {
                // Best-effort: turn sync is opportunistic
            }
        }
    }

    /**
     * Annulla la sottoscrizione al topic MQTT dei turni.
     * <p>
     * Rimuove la sottoscrizione per il topic di turno corrente e
     * resetta i riferimenti al subscriber e al topic. Non produce
     * effetti se non ci sono sottoscrizioni attive.
     */
    private void unsubscribeTurnTopic() {
        if (turnSubscriber != null && currentTurnTopic != null && mqttAdapter != null) {
            try {
                mqttAdapter.unsubscribe(currentTurnTopic);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        turnSubscriber = null;
        currentTurnTopic = null;
    }

    /**
     * Imposta lo stato di attesa nella lobby.
     * <p>
     * Abilita solo il pulsante di avvio e mostra un messaggio informativo
     * nell'area centrale.
     */
    private void setInLobbyState() {
        startButton.setDisable(false);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
        controlsArea.getChildren().clear();
        controlsArea.getChildren().add(new Label("Select a game and press Start") {{
            setStyle("-fx-text-fill: #666; -fx-font-size: 14;");
        }});
        setCenterContent(controlsArea);
    }

    /**
     * Imposta lo stato di partita in esecuzione.
     * <p>
     * Abilita i pulsanti di pausa e stop, disabilita start e resume,
     * nasconde il pulsante di ritorno alla home e resetta il flag
     * di pausa locale a false.
     */
    private void setGameRunningState() {
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
        this.localPaused = false;
    }

    /**
     * Imposta lo stato di partita in pausa.
     * <p>
     * Abilita i pulsanti di stop e resume, disabilita start e pausa,
     * nasconde il pulsante di ritorno alla home e imposta il flag
     * di pausa locale a true.
     */
    private void setGamePausedState() {
        startButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(false);
        resumeButton.setDisable(false);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
        this.localPaused = true;
    }

    /**
     * Imposta lo stato di partita conclusa.
     * <p>
     * Abilita solo il pulsante di ritorno alla home, disabilita tutti
     * gli altri e mostra il messaggio "Match ended" nell'area centrale.
     */
    private void setGameEndedState() {
        startButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(false);
        backToHomeButton.setVisible(true);
        controlsArea.getChildren().clear();
        controlsArea.getChildren().add(new Label("Match ended") {{
            setStyle("-fx-text-fill: #27ae60; -fx-font-size: 16; -fx-font-weight: bold;");
        }});
        setCenterContent(controlsArea);
    }

    /**
     * Imposta il testo dell'etichetta di stato.
     *
     * @param msg il messaggio di stato da visualizzare; non null
     */
    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    /**
     * Mostra un dialogo di errore modale.
     *
     * @param msg il messaggio di errore da visualizzare; non null
     */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    /**
     * Crea un pulsante con testo e colore di sfondo personalizzati.
     *
     * @param text  il testo del pulsante; non null
     * @param color il colore di sfondo in formato CSS (es. "#27ae60"); non null
     * @return un {@link Button} con lo stile applicato
     */
    private Button createButton(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 13; -fx-padding: 8 18; -fx-background-radius: 6;");
        return b;
    }
}
