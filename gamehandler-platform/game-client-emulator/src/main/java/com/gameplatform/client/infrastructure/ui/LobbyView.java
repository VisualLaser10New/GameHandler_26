package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import com.gameplatform.client.infrastructure.mqtt.StateSubscriber;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.LobbyJoinPayload;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * JavaFX lobby screen shown between game selection and active gameplay.
 * <p>
 * Operates in two modes depending on the selected game machine status:
 * <ul>
 *   <li><b>CREATOR</b> — the first player creates a lobby, then waits for
 *       others to join. When enough players have joined, the host can start
 *       the session.</li>
 *   <li><b>JOINER</b> — a second (or later) player sees the active lobby
 *       and joins it with one click.</li>
 * </ul>
 * Real-time participant updates are received via MQTT by subscribing to
 * {@code building/.../game/.../session/lobby/+} topics.
 */
public class LobbyView {

    private final VBox root;
    private final Label titleLabel;
    private final Label modeLabel;
    private final Label infoLabel;
    private final VBox participantsBox;
    private final Button actionButton;   // "Crea Lobby" or "Unisciti"
    private final Button startButton;    // visible only for creator after lobby created
    private final Button backButton;

    private final SessionPublisher sessionPublisher;
    private final MqttClientAdapter mqttAdapter;
    private final String buildingId;

    private GameStateDto currentGame;
    private String currentUsername = "player";
    private String lobbySessionId;       // assigned by server after create
    private final List<String> participants = new ArrayList<>();

    private Runnable onCancel;
    /** Called when the lobby session has started — passes (GameStateDto, sessionId, participants). */
    private TriConsumer<GameStateDto, String, List<String>> onLobbyStarted;

    // Functional interface for 3-arg callback
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    public LobbyView(SessionPublisher sessionPublisher, MqttClientAdapter mqttAdapter, String buildingId) {
        this.sessionPublisher = sessionPublisher;
        this.mqttAdapter = mqttAdapter;
        this.buildingId = buildingId;

        root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 30; -fx-background-color: #1e1e1e;");

        titleLabel = new Label("Lobby");
        titleLabel.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #eee;");

        modeLabel = new Label("");
        modeLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #3498db;");

        infoLabel = new Label("");
        infoLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #aaa;");

        Label participantsTitle = new Label("Giocatori nella lobby:");
        participantsTitle.setStyle("-fx-font-size: 13; -fx-text-fill: #ccc;");

        participantsBox = new VBox(6);
        participantsBox.setAlignment(Pos.CENTER_LEFT);
        participantsBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 12; -fx-background-radius: 6;");
        participantsBox.setMinWidth(260);

        actionButton = new Button("Crea Lobby");
        actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
        actionButton.setOnAction(e -> handleActionButton());

        startButton = new Button("▶  Avvia Partita");
        startButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
        startButton.setVisible(false);
        startButton.setOnAction(e -> startLobby());

        backButton = new Button("← Torna alla selezione");
        backButton.setStyle("-fx-background-color: #555; -fx-text-fill: #ccc; -fx-padding: 8 20;");
        backButton.setOnAction(e -> handleBackButton());

        HBox buttons = new HBox(14, actionButton, startButton, backButton);
        buttons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(titleLabel, modeLabel, infoLabel, participantsTitle, participantsBox, buttons);
    }

    // ─────────────────────────── Public API ───────────────────────────────────

    /** Returns the root JavaFX node for this view. */
    public Parent getView() { return root; }

    /** Sets the current user's username (used as creatorId / joinerId). */
    public void setCurrentUser(String username) {
        if (username != null && !username.isBlank()) this.currentUsername = username;
    }

    /** Called when the user cancels and wants to go back to game selection. */
    public void setOnCancel(Runnable callback) { this.onCancel = callback; }

    /** Called when the lobby session is fully started by the host. */
    public void setOnLobbyStarted(TriConsumer<GameStateDto, String, List<String>> callback) {
        this.onLobbyStarted = callback;
    }

    /**
     * Configures the lobby view for the given game machine.
     * Determines whether this client acts as CREATOR or JOINER.
     *
     * @param state the selected game machine state
     */
    public void configure(GameStateDto state) {
        this.currentGame = state;
        this.participants.clear();
        this.lobbySessionId = null;

        // Reset button enabled/disabled state — otherwise state from a previous
        // lobby visit (which disabled actionButton/startButton) leaks across
        // navigations and the user can no longer create or join a lobby until
        // the client is restarted.
        actionButton.setDisable(false);
        startButton.setDisable(true);
        startButton.setVisible(false);

        titleLabel.setText("Lobby — " + state.name() + " [" + state.gameType() + "]");

        boolean isCreator = state.status() == GameMachineStatus.AVAILABLE;

        if (isCreator) {
            boolean singlePlayer = state.maxPlayers() == 1;
            if (singlePlayer) {
                modeLabel.setText("Gioco per giocatore singolo");
                actionButton.setText("▶  Gioca!");
                actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
                infoLabel.setText("Premi 'Gioca' per iniziare subito la partita.");
            } else {
                modeLabel.setText("Sei il primo giocatore — crea la lobby");
                actionButton.setText("Crea Lobby");
                actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
                infoLabel.setText("Gli altri giocatori potranno unirsi dopo che hai creato la lobby.");
            }
        } else {
            // JOINER mode: the game machine is in LOBBY status, meaning a lobby
            // session already exists on the server. We fetch the active session
            // id via REST (see fetchActiveLobbySession) so the joiner can press
            // "Unisciti" immediately, without waiting for an MQTT join event.
            modeLabel.setText("Lobby attiva — unisciti alla partita");
            actionButton.setText("Unisciti alla Lobby");
            actionButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
            infoLabel.setText("Premi 'Unisciti' per entrare nella lobby attiva.");
            fetchActiveLobbySession(state.gameId());
        }

        // Subscribe to lobby MQTT events for real-time participant updates.
        // Use the buildingId from the server's GameStateDto (not the local default)
        // to ensure the subscription topic matches the server's publish topic.
        if (mqttAdapter != null && mqttAdapter.isConnected()) {
            String mqttBuildingId = state.buildingId() != null ? state.buildingId() : buildingId;
            StateSubscriber subscriber = new StateSubscriber(mqttAdapter, mqttBuildingId,
                    (topic, payload) -> Platform.runLater(() -> handleLobbyMqttMessage(topic, payload)));
            subscriber.subscribeToLobbyEvents(state.gameId());
        }

        refreshParticipantsBox();
    }

    // ─────────────────────────── Active lobby lookup ──────────────────────────

    /**
     * Asks the Local Server for the active lobby session of the given game
     * machine via {@code GET /api/sessions/lobby/active?gameId=...}. On success the
     * {@code lobbySessionId} field is populated so the JOINER can immediately
     * press "Unisciti". On 404 (no active lobby) the user is informed and the
     * action button is disabled, since there is nothing to join.
     *
     * @param gameId the game machine identifier
     */
    private void fetchActiveLobbySession(String gameId) {
        try {
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "https://localhost:8081");
            java.net.http.HttpClient client = HttpClientHelper.getHttpClient(localServerUrl);
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(localServerUrl + "/api/sessions/lobby/active?gameId=" + gameId))
                    .GET();
            String token = HttpClientHelper.getToken();
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            java.net.http.HttpRequest request = requestBuilder.build();
            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response.statusCode() == 200) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper =
                                        new com.fasterxml.jackson.databind.ObjectMapper()
                                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                                GameSessionDto session = mapper.readValue(response.body(), GameSessionDto.class);
                                lobbySessionId = session.id();
                                infoLabel.setText("Lobby attiva trovata. Premi 'Unisciti' per entrare.");
                                if (!participants.contains(currentUsername)) {
                                    participants.add(currentUsername);
                                    refreshParticipantsBox();
                                }
                            } catch (Exception e) {
                                infoLabel.setText("Errore lettura sessione lobby: " + e.getMessage());
                            }
                        } else if (response.statusCode() == 404) {
                            infoLabel.setText("Nessuna lobby attiva per questo gioco. Torna indietro e riprova.");
                            actionButton.setDisable(true);
                        } else {
                            infoLabel.setText("Impossibile recuperare la lobby (HTTP " + response.statusCode() + ").");
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> infoLabel.setText("Errore di connessione al server: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            infoLabel.setText("Errore avvio richiesta lobby: " + e.getMessage());
        }
    }

    // ─────────────────────────── Button handlers ──────────────────────────────

    private void handleActionButton() {
        if (currentGame == null) return;

        boolean isCreator = currentGame.status() == GameMachineStatus.AVAILABLE
                || lobbySessionId == null;

        if (isCreator && lobbySessionId == null) {
            // CREATE LOBBY
            if (sessionPublisher != null) {
                sessionPublisher.publishLobbyCreate(currentGame.gameId(), currentGame.gameType(), currentUsername);
            }
            participants.clear();
            participants.add(currentUsername);
            boolean singlePlayer = currentGame.maxPlayers() == 1;
            if (singlePlayer) {
                infoLabel.setText("Avvio partita...");
            } else {
                int minPlayers = currentGame.minPlayers();
                int maxPlayers = currentGame.maxPlayers();
                infoLabel.setText("Lobby creata! In attesa di altri giocatori (minimo " + minPlayers
                        + ", massimo " + maxPlayers + ")...");
                startButton.setVisible(true);
                startButton.setDisable(true); // enabled when enough players join via MQTT
            }
            actionButton.setDisable(true);
            refreshParticipantsBox();
        } else {
            // JOIN LOBBY
            if (sessionPublisher != null && lobbySessionId != null) {
                sessionPublisher.publishLobbyJoin(currentGame.gameId(), lobbySessionId, currentUsername);
            } else {
                infoLabel.setText("Sessione lobby non ancora disponibile — attendi un momento.");
            }
            actionButton.setDisable(true);
        }
    }

    private void startLobby() {
        if (sessionPublisher != null && lobbySessionId != null) {
            sessionPublisher.publishLobbyStart(currentGame.gameId(), lobbySessionId);
            infoLabel.setText("Partita avviata!");
            startButton.setDisable(true);
        }
    }

    /**
     * Back button handler.
     * <p>
     * If this client is the creator of an active lobby and no other players have
     * joined yet, the lobby is cancelled on the server (the session is aborted
     * and the game machine is released back to AVAILABLE) before navigating back.
     * If other players have already joined, the lobby is left active so the
     * remaining participants can keep playing — only navigation occurs.
     */
    private void handleBackButton() {
        if (isCreatorOfActiveLobby()) {
            try {
                sessionPublisher.publishLobbyCancel(currentGame.gameId(), lobbySessionId, currentUsername);
            } catch (Exception ignored) {
                // Best-effort cancel; navigate back regardless
            }
        }
        if (onCancel != null) onCancel.run();
    }

    /**
     * Determines whether this client may cancel the lobby on back navigation:
     * the creator (first participant), with a confirmed session id, and no
     * other players joined.
     */
    private boolean isCreatorOfActiveLobby() {
        if (lobbySessionId == null || currentGame == null || sessionPublisher == null) {
            return false;
        }
        if (participants.isEmpty() || !participants.get(0).equals(currentUsername)) {
            return false;
        }
        // Only the creator alone in the lobby -> cancel allowed.
        // Any other participant -> lobby must stay active.
        return participants.size() <= 1;
    }

    // ─────────────────────────── MQTT ─────────────────────────────────────────

    private void handleLobbyMqttMessage(String topic, byte[] payload) {
        try {
            String[] tokens = topic.split("/");
            if (tokens.length < 7) return;
            String lobbyAction = tokens[6]; // lobby/create, lobby/join, lobby/start

            switch (lobbyAction) {
                case "create" -> {
                    // Server confirmed lobby creation, extract sessionId from payload
                    // The server broadcasts back a SessionStartPayload or LobbyCreatePayload.
                    // We parse it as a generic JSON map to extract the sessionId.
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<?, ?> map = mapper.readValue(payload, java.util.Map.class);
                    Object sid = map.get("sessionId");
                    if (sid != null && !sid.toString().isBlank()) {
                        lobbySessionId = sid.toString();
                        // For single-player games (maxPlayers == 1), auto-start
                        // immediately — no need to wait for other players.
                        if (currentGame != null && currentGame.maxPlayers() == 1) {
                            startLobby();
                        } else {
                            startButton.setDisable(true);
                            int minPlayers = currentGame != null ? currentGame.minPlayers() : 1;
                            int currentSize = participants.size();
                            infoLabel.setText("Lobby confermata (ID: " + lobbySessionId.substring(0, 8)
                                    + "...). In attesa di giocatori (" + currentSize + "/" + minPlayers + ").");
                        }
                    }
                }
                case "join" -> {
                    // A new player joined — extract sessionId (if not already set) and userId
                    LobbyJoinPayload joinPayload = MqttPayloadSerializer.deserialize(payload, LobbyJoinPayload.class);
                    if (lobbySessionId == null && joinPayload.sessionId() != null) {
                        lobbySessionId = joinPayload.sessionId();
                    }
                    String joined = joinPayload.userId();
                    if (joined != null && !participants.contains(joined)) {
                        participants.add(joined);
                        refreshParticipantsBox();
                        if (!participants.contains(currentUsername)) {
                            participants.add(currentUsername);
                            refreshParticipantsBox();
                        }
                        // Enable "Avvia" only when enough players have joined.
                        int minPlayers = currentGame != null ? currentGame.minPlayers() : 1;
                        if (participants.size() >= minPlayers) {
                            startButton.setDisable(false);
                            infoLabel.setText(joined + " si è unito alla lobby. (" + participants.size()
                                    + " giocatori) — Puoi avviare la partita!");
                        } else {
                            infoLabel.setText(joined + " si è unito alla lobby. (" + participants.size()
                                    + "/" + minPlayers + " giocatori minimi)");
                        }
                    }
                }
                case "start" -> {
                    // Server confirmed start — fire the callback to navigate to GamePlayView
                    if (onLobbyStarted != null && currentGame != null) {
                        onLobbyStarted.accept(currentGame, lobbySessionId, new ArrayList<>(participants));
                    }
                }
                case "cancel" -> {
                    // Lobby was cancelled (e.g. creator left). Inform the user.
                    infoLabel.setText("La lobby è stata chiusa. Torna alla selezione.");
                    actionButton.setDisable(true);
                    startButton.setDisable(true);
                    lobbySessionId = null;
                }
            }
        } catch (Exception e) {
            // Ignore parse errors silently
        }
    }

    private void refreshParticipantsBox() {
        participantsBox.getChildren().clear();
        if (participants.isEmpty()) {
            Label empty = new Label("Nessun giocatore ancora");
            empty.setStyle("-fx-text-fill: #666; -fx-font-size: 12;");
            participantsBox.getChildren().add(empty);
        } else {
            for (int i = 0; i < participants.size(); i++) {
                String p = participants.get(i);
                String icon = (i == 0) ? "👑 " : "👤 ";
                Label l = new Label(icon + p + (p.equals(currentUsername) ? "  (tu)" : ""));
                l.setStyle("-fx-text-fill: " + (i == 0 ? "#f1c40f" : "#ddd") + "; -fx-font-size: 13;");
                participantsBox.getChildren().add(l);
            }
        }
    }
}
