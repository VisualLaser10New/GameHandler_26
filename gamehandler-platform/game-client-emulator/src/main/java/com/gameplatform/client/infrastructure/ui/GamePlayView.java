package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.GameOrchestrationService;
import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.client.infrastructure.ui.components.ScoreboardComponent;
import com.gameplatform.client.infrastructure.ui.components.TimerComponent;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JavaFX view displayed during an active game session.
 * <p>
 * Shows the game name and type at the top, a {@link ScoreboardComponent}
 * and {@link TimerComponent} on the left, and control buttons (Start,
 * Pause, Stop, Resume) in the centre. The view creates a game instance
 * via {@link com.gameplatform.client.domain.GameFactory} when the user
 * presses Start and wires through to {@link GameOrchestrationService}
 * when available.
 */
public class GamePlayView {
    private final BorderPane root;
    private final ScoreboardComponent scoreboard;
    private final TimerComponent timer;
    private final Label gameInfoLabel;
    private final VBox controlsArea;
    private final Button startButton;
    private final Button pauseButton;
    private final Button stopButton;
    private final Button resumeButton;
    private GameLifecycle currentGame;
    private GameOrchestrationService orchestrationService;
    private GameStateDto currentGameState;

    public GamePlayView() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        gameInfoLabel = new Label("No game selected");
        gameInfoLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #eee; -fx-padding: 10;");

        scoreboard = new ScoreboardComponent();
        timer = new TimerComponent();

        VBox leftPanel = new VBox(10, scoreboard, timer);
        leftPanel.setStyle("-fx-padding: 10;");

        controlsArea = new VBox(10);
        controlsArea.setAlignment(Pos.CENTER);

        startButton = new Button("Start Game");
        startButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 20;");

        pauseButton = new Button("Pause");
        pauseButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 20;");

        stopButton = new Button("Stop Game");
        stopButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 20;");

        resumeButton = new Button("Resume");
        resumeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 20;");

        startButton.setOnAction(e -> startGame());
        pauseButton.setOnAction(e -> pauseGame());
        stopButton.setOnAction(e -> stopGame());
        resumeButton.setOnAction(e -> resumeGame());

        HBox buttonBar = new HBox(10, startButton, pauseButton, stopButton, resumeButton);
        buttonBar.setAlignment(Pos.CENTER);
        controlsArea.getChildren().add(buttonBar);

        root.setTop(gameInfoLabel);
        root.setLeft(leftPanel);
        root.setCenter(controlsArea);

        setGameControlsVisible(false);
    }

    /**
     * Returns the root JavaFX node for this view.
     *
     * @return the game play {@link Parent}
     */
    public Parent getView() {
        return root;
    }

    /**
     * Injects the {@link GameOrchestrationService} for full lifecycle
     * coordination once the service is implemented.
     *
     * @param service the orchestration service instance
     */
    public void setOrchestrationService(GameOrchestrationService service) {
        this.orchestrationService = service;
    }

    /**
     * Configures the view for a specific game machine.
     *
     * @param state the selected game's state DTO
     */
    public void setGameState(GameStateDto state) {
        this.currentGameState = state;
        gameInfoLabel.setText(state.name() + "  [" + state.gameType() + "]");
        setGameControlsVisible(true);
    }

    private void setGameControlsVisible(boolean visible) {
        startButton.setVisible(visible);
        pauseButton.setVisible(visible);
        stopButton.setVisible(visible);
        resumeButton.setVisible(visible);
    }

    private void startGame() {
        if (currentGameState == null) return;

        List<UserId> participants = List.of(new UserId("local-user"));
        currentGame = com.gameplatform.shared.domain.game.GameFactory.createGame(
                currentGameState.gameType(),
                new GameSessionId(java.util.UUID.randomUUID().toString()));
        currentGame.start(participants);

        timer.startTimer();
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resumeButton.setDisable(true);

        updateGameDisplay();
    }

    private void pauseGame() {
        if (currentGame != null) {
            currentGame.stop(StopReason.COMPLETED);
        }
        timer.stopTimer();
        startButton.setDisable(true);
        pauseButton.setDisable(true);
        stopButton.setDisable(false);
        resumeButton.setDisable(false);
    }

    private void stopGame() {
        if (currentGame != null) {
            currentGame.stop(StopReason.COMPLETED);
        }
        timer.stopTimer();
        startButton.setDisable(false);
        pauseButton.setDisable(true);
        stopButton.setDisable(true);
        resumeButton.setDisable(true);
        currentGame = null;
        scoreboard.updateScores(null);
    }

    private void resumeGame() {
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resumeButton.setDisable(true);
        timer.startTimer();
    }

    /**
     * Refreshes the scoreboard display with the current participants of the
     * active game. Each participant is shown with a default score of zero.
     */
    public void updateGameDisplay() {
        if (currentGame == null) return;

        Map<String, Integer> scores = currentGame.getParticipants().stream()
                .collect(Collectors.toMap(
                        UserId::value,
                        u -> 0
                ));
        scoreboard.updateScores(scores);
    }
}
