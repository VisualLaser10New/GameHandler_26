package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.mqtt.StateSubscriber;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * JavaFX view that lists the available game machines in the building.
 * <p>
 * Games are displayed with colour-coded status indicators (green for
 * available, red for in-use, yellow otherwise). The list can be refreshed
 * via an HTTP GET to {@code /api/games} and receives real-time updates
 * through an MQTT {@link StateSubscriber}. Selecting an available game
 * and pressing "Play" triggers the {@code onGameSelected} callback.
 */
public class GameSelectionView {
    private final VBox root;
    private final ObservableList<GameStateDto> games;
    private final ListView<GameStateDto> gameList;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button playButton;
    private Consumer<GameStateDto> onGameSelected;

    /**
     * Creates the game selection view.
     *
     * @param mqttAdapter the MQTT adapter for real-time state subscriptions;
     *                    may be {@code null}
     * @param buildingId  the building identifier used in MQTT topic filters;
     *                    may be {@code null}
     */
    public GameSelectionView(MqttClientAdapter mqttAdapter, String buildingId) {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Available Games");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        games = FXCollections.observableArrayList();
        gameList = new ListView<>(games);
        gameList.setPrefHeight(300);
        gameList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GameStateDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String suffix = item.status() == GameMachineStatus.LOBBY ? "  (lobby aperta)" : "";
                    setText(item.name() + "  [" + item.gameType() + "]  -  " + item.status() + suffix);
                    if (item.status() == GameMachineStatus.AVAILABLE) {
                        setStyle("-fx-text-fill: #2ecc71;");
                    } else if (item.status() == GameMachineStatus.LOBBY) {
                        setStyle("-fx-text-fill: #9b59b6;");
                    } else if (item.status() == GameMachineStatus.IN_USE) {
                        setStyle("-fx-text-fill: #e74c3c;");
                    } else {
                        setStyle("-fx-text-fill: #f39c12;");
                    }
                }
            }
        });

        statusLabel = new Label("Loading games...");
        statusLabel.setStyle("-fx-text-fill: #aaa;");

        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);

        refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 6 16;");

        playButton = new Button("Play Selected");
        playButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        playButton.setDisable(true);

        buttonBar.getChildren().addAll(refreshButton, playButton);
        content.getChildren().addAll(title, gameList, statusLabel, buttonBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        root = new VBox(scrollPane);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");

        refreshButton.setOnAction(e -> refreshGames());
        playButton.setOnAction(e -> {
            GameStateDto selected = gameList.getSelectionModel().getSelectedItem();
            if (selected != null && onGameSelected != null) {
                onGameSelected.accept(selected);
            }
        });
        gameList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            // Allow selecting AVAILABLE games (to create a new lobby) and
            // LOBBY games (to join an existing lobby). IN_USE, RESERVED and
            // MAINTENANCE games cannot be joined.
            boolean selectable = sel != null
                    && (sel.status() == GameMachineStatus.AVAILABLE
                        || sel.status() == GameMachineStatus.LOBBY);
            playButton.setDisable(!selectable);
        });

        if (mqttAdapter != null && buildingId != null) {
            StateSubscriber subscriber = new StateSubscriber(mqttAdapter, buildingId, (topic, payload) -> {
                try {
                    // The server publishes a lightweight GameStatePayload
                    // ({gameId, status, userId}) on the state topic — NOT a
                    // full GameStateDto.  Deserializing it as GameStateDto
                    // fails (unknown 'userId' property) and the update is
                    // silently dropped, leaving the list stale.  Parse the
                    // correct type and merge only the status into the
                    // existing cached game entry, preserving name/gameType/
                    // minPlayers/maxPlayers/buildingId from the REST load.
                    GameStatePayload stateMsg = MqttPayloadSerializer.deserialize(payload, GameStatePayload.class);
                    Platform.runLater(() -> updateGameStatus(stateMsg.gameId(), stateMsg.status()));
                } catch (Exception e) {
                    // ignore deserialize errors on wildcard topics
                }
            });
            subscriber.subscribeToStates();
        }
    }

    /**
     * Returns the root JavaFX node for this view.
     *
     * @return the game selection {@link Parent}
     */
    public Parent getView() {
        return root;
    }

    /**
     * Fetches the latest list of games from the Local Server via
     * {@code GET /api/games} and updates the displayed list.
     * The status label shows the number of games loaded or an error
     * description if the request fails.
     */
    public void refreshGames() {
        statusLabel.setText("Refreshing...");
        try {
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "https://localhost:8081");
            java.net.http.HttpClient client = com.gameplatform.client.infrastructure.security.HttpClientHelper.getHttpClient(localServerUrl);
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(localServerUrl + "/api/games"))
                    .GET();
            String token = com.gameplatform.client.infrastructure.security.HttpClientHelper.getToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            java.net.http.HttpRequest request = requestBuilder.build();
            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response.statusCode() == 200) {
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                    com.fasterxml.jackson.core.type.TypeReference<List<GameStateDto>> typeRef =
                                            new com.fasterxml.jackson.core.type.TypeReference<>() {};
                                    List<GameStateDto> updated = mapper.readValue(response.body(), typeRef);
                                    games.setAll(updated);
                                    statusLabel.setText(updated.size() + " games loaded");
                                } catch (Exception e) {
                                    statusLabel.setText("Parse error: " + e.getMessage());
                                }
                            } else {
                                statusLabel.setText("Failed to load games: " + response.statusCode());
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> statusLabel.setText("Connection error: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Merges a status update (received via MQTT as a
     * {@link GameStatePayload}) into the current game list, preserving
     * the full game descriptor (name, gameType, min/max players,
     * buildingId) loaded from REST. Only the {@code status} field is
     * replaced.
     *
     * @param gameId   the game machine identifier whose status changed
     * @param newStatus the new machine status
     */
    private void updateGameStatus(String gameId, GameMachineStatus newStatus) {
        for (int i = 0; i < games.size(); i++) {
            GameStateDto existing = games.get(i);
            if (existing.gameId().equals(gameId)) {
                games.set(i, new GameStateDto(
                        existing.gameId(),
                        existing.gameType(),
                        existing.name(),
                        existing.buildingId(),
                        newStatus,
                        existing.minPlayers(),
                        existing.maxPlayers()
                ));
                return;
            }
        }
        // Game not in the list yet (shouldn't happen for state-only
        // updates); ignore rather than add a corrupted partial entry.
    }

    /**
     * Registers a callback invoked when the user selects an available game
     * and presses the "Play" button.
     *
     * @param callback the action to run with the selected {@link GameStateDto}
     */
    public void setOnGameSelected(Consumer<GameStateDto> callback) {
        this.onGameSelected = callback;
    }
}
