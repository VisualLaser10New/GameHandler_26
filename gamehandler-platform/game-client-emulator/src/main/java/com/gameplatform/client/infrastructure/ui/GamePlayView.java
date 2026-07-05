package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.GameOrchestrationService;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import com.gameplatform.client.infrastructure.ui.components.ScoreboardComponent;
import com.gameplatform.client.infrastructure.ui.components.TimerComponent;
import com.gameplatform.client.infrastructure.ui.panels.*;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * JavaFX view displayed during an active game session.
 * <p>
 * Shows the game name and type at the top, a {@link ScoreboardComponent}
 * and {@link TimerComponent} on the left, a game-specific emulation panel
 * in the centre, and lifecycle control buttons (Start, Pause, Resume, Stop)
 * at the bottom. Session management is delegated to
 * {@link GameOrchestrationService} via MQTT; the game-specific UI panel is
 * built by {@link #buildGamePanel()} after the session starts.
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
    private GameStateDto currentGameState;
    private String currentUsername = "player";
    private String gameId = "game-1";
    private GamePanel activePanel;

    // Lobby-provided participants (set when coming from LobbyView)
    private List<String> lobbyParticipants;
    private String lobbySessionId;

    // Callback to navigate back to game selection after the match ends
    private Runnable onBackToHome;

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

        startButton = createButton("▶  Avvia Partita", "#27ae60");
        pauseButton = createButton("⏸  Pausa", "#f39c12");
        stopButton  = createButton("⏹  Termina", "#e74c3c");
        resumeButton = createButton("▶  Riprendi", "#3498db");
        backToHomeButton = createButton("←  Torna alla home", "#7f8c8d");

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

    /** Returns the root JavaFX node for this view. */
    public Parent getView() {
        return root;
    }

    /** Injects the orchestration service. Must be called before showing this view. */
    public void setOrchestrationService(GameOrchestrationService service) {
        this.orchestrationService = service;
    }

    /** Injects the session publisher (used for pause/resume topics). */
    public void setSessionPublisher(SessionPublisher publisher) {
        this.sessionPublisher = publisher;
    }

    /** Sets the username of the logged-in user (used as participant). */
    public void setCurrentUser(String username) {
        if (username != null && !username.isBlank()) this.currentUsername = username;
    }

    /** Sets the game machine ID (from env GAME_ID). */
    public void setGameId(String id) {
        if (id != null && !id.isBlank()) this.gameId = id;
    }

    /** Called when the user wants to go back to game selection after the match ends. */
    public void setOnBackToHome(Runnable callback) { this.onBackToHome = callback; }

    /**
     * Configures the view for a specific game machine.
     *
     * @param state the selected game's state DTO
     */
    public void setGameState(GameStateDto state) {
        this.currentGameState = state;
        this.lobbyParticipants = null;
        this.lobbySessionId = null;
        gameInfoLabel.setText(state.name() + "  [" + state.gameType() + "]");
        statusLabel.setText("Pronto per avviare la partita");
        setInLobbyState();
    }

    /**
     * Configures the view when entering from a lobby session that is already
     * started by the server (lobby flow).
     *
     * @param state        the game state DTO
     * @param sessionId    the session ID assigned by the server
     * @param participants the confirmed participant list
     */
    public void setFromLobby(GameStateDto state, String sessionId, List<String> participants) {
        this.currentGameState = state;
        this.lobbySessionId = sessionId;
        this.lobbyParticipants = participants;
        gameInfoLabel.setText(state.name() + "  [" + state.gameType() + "]");
        statusLabel.setText("Lobby avviata — partita in corso");
        buildGamePanel();
        timer.startTimer();
        setGameRunningState();
    }

    // ─────────────────────────── Button handlers ──────────────────────────────

    private void startGame() {
        if (currentGameState == null) return;
        setStatus("Avvio in corso...");
        startButton.setDisable(true);

        List<String> participants = lobbyParticipants != null
                ? lobbyParticipants
                : List.of(currentUsername);

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
                        setStatus("Partita in corso");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        showError("Impossibile avviare la partita: " + ex.getMessage());
                        startButton.setDisable(false);
                        setStatus("Errore avvio");
                    });
                }
            }, "start-game-thread").start();
        } else {
            // Fallback: local-only mode (no server wired)
            buildGamePanel();
            timer.startTimer();
            setGameRunningState();
            setStatus("Partita in corso (locale)");
        }
    }

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
        timer.stopTimer();
        setGamePausedState();
        setStatus("Partita in pausa");
    }

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
        timer.resumeTimer();
        setGameRunningState();
        setStatus("Partita in corso");
    }

    private void stopGame() {
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

        // Publish session/end to the server so it can release the game machine.
        // We must use the gameId from currentGameState (e.g. "slot-machine-1"),
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

        timer.stopTimer();
        root.setCenter(controlsArea);
        activePanel = null;
        scoreboard.updateScores(null);
        setGameEndedState();
        setStatus("Partita terminata");
    }

    // ─────────────────────────── Helpers ──────────────────────────────────────

    /**
     * Builds and displays the game-specific emulation panel based on
     * the currently selected game type.
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
        // Wire the panel's score updates to the lateral scoreboard so that
        // incremental changes during a match (e.g. slot payout, foosball
        // goal, dart throw) propagate in real time. Without this, the
        // scoreboard stays frozen at the zero-initialized seed.
        activePanel.setScoreConsumer(scoreboard::updateScores);
        root.setCenter(activePanel.getView());

        // Seed scoreboard with zero scores
        java.util.Map<String, Integer> scores = new java.util.LinkedHashMap<>();
        participants.forEach(p -> scores.put(p, 0));
        scoreboard.updateScores(scores);
    }

    private void setInLobbyState() {
        startButton.setDisable(false);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
        controlsArea.getChildren().clear();
        controlsArea.getChildren().add(new Label("Seleziona un gioco e premi Avvia") {{
            setStyle("-fx-text-fill: #666; -fx-font-size: 14;");
        }});
        root.setCenter(controlsArea);
    }

    private void setGameRunningState() {
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
    }

    private void setGamePausedState() {
        startButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(false);
        resumeButton.setDisable(false);
        backToHomeButton.setDisable(true);
        backToHomeButton.setVisible(false);
    }

    /** State shown after the match ends: only the "back to home" button is enabled. */
    private void setGameEndedState() {
        startButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        resumeButton.setDisable(true);
        backToHomeButton.setDisable(false);
        backToHomeButton.setVisible(true);
        controlsArea.getChildren().clear();
        controlsArea.getChildren().add(new Label("Partita terminata") {{
            setStyle("-fx-text-fill: #27ae60; -fx-font-size: 16; -fx-font-weight: bold;");
        }});
        root.setCenter(controlsArea);
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Errore");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private Button createButton(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 13; -fx-padding: 8 18; -fx-background-radius: 6;");
        return b;
    }
}
