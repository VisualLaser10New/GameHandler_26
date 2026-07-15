package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.domain.exception.ServerUnavailableException;
import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import com.gameplatform.client.infrastructure.mqtt.StateSubscriber;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import com.gameplatform.shared.mqtt.payload.LobbyJoinPayload;
import com.gameplatform.shared.mqtt.payload.LobbyLeavePayload;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

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
    private String currentUserId;
    private String lobbySessionId;       // assigned by server after create
    private final List<String> participants = new ArrayList<>();
    // Role determined once in configure() based on the game status at
    // selection time: AVAILABLE → this client creates the lobby; LOBBY →
    // this client joins an existing lobby.  Stored as a field so that
    // handleActionButton() does not recompute it with a fragile boolean
    // expression that would misclassify a joiner (whose lobbySessionId is
    // still null because fetchActiveLobbySession hasn't returned yet) as
    // a creator and erroneously try to create a second lobby on an
    // already-LOBBY game machine.
    private boolean creatorMode;
    // Set to true the moment this client publishes a lobby/create
    // message.  Used to distinguish "I created this lobby" from "someone
    // else created a lobby" in handleStateMqttMessage: the server
    // publishes state=LOBBY BEFORE the lobby/create echo, so without
    // this guard the creator would receive their own state update and
    // be downgraded to joiner (losing the ability to start the game).
    private boolean lobbyCreateInitiated;

    // MQTT subscription topics created in configure(); tracked so they
    // can be unsubscribed in cleanup() when the user navigates away.
    // Without this, stale subscriptions would deliver lobby events from
    // OTHER players' sessions to this view, causing the screen to
    // unexpectedly switch to GamePlayView (the "slot machine opens for
    // A when B starts a game" bug).
    private String lobbyTopicFilter;
    private String stateTopicFilter;
    private boolean subscribed;

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

        Label participantsTitle = new Label("Players in the lobby:");
        participantsTitle.setStyle("-fx-font-size: 13; -fx-text-fill: #ccc;");

        participantsBox = new VBox(6);
        participantsBox.setAlignment(Pos.CENTER_LEFT);
        participantsBox.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 12; -fx-background-radius: 6;");
        participantsBox.setMinWidth(260);

        actionButton = new Button("Create Lobby");
        actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
        actionButton.setOnAction(e -> handleActionButton());

        startButton = new Button("▶  Start Match");
        startButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
        startButton.setVisible(false);
        startButton.setOnAction(e -> startLobby());

        backButton = new Button("← Back to selection");
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

    /**
     * Sets the authenticated user's stable id (UUID resolved from
     * {@code /api/auth/me}). For single-player games this is sent to the
     * server as the lobby creator identity (and therefore the session
     * participant) so the Central {@code player_statistics} /
     * {@code player_match_facts} read-models key statistics on the user id,
     * matching the {@code /api/players/me/statistics} query. May be
     * {@code null} (e.g. user not yet locally replicated); in that case the
     * username fallback keeps the historical behaviour.
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
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
        this.creatorMode = state.status() == GameMachineStatus.AVAILABLE;
        this.lobbyCreateInitiated = false;

        // Reset button enabled/disabled state — otherwise state from a previous
        // lobby visit (which disabled actionButton/startButton) leaks across
        // navigations and the user can no longer create or join a lobby until
        // the client is restarted.
        actionButton.setDisable(false);
        startButton.setDisable(true);
        startButton.setVisible(false);

        titleLabel.setText("Lobby — " + state.name() + " [" + state.gameType() + "]");

        if (creatorMode) {
            boolean singlePlayer = state.maxPlayers() == 1;
            if (singlePlayer) {
                modeLabel.setText("Single-player game");
                actionButton.setText("▶  Play!");
                actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
                infoLabel.setText("Press 'Play' to start the match right away.");
            } else {
                modeLabel.setText("You are the first player — create the lobby");
                actionButton.setText("Create Lobby");
                actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
                infoLabel.setText("Other players will be able to join after you create the lobby.");
            }
        } else {
            // JOINER mode: the game machine is in LOBBY status, meaning a lobby
            // session already exists on the server. We fetch the active session
            // id via REST (see fetchActiveLobbySession) so the joiner can press
            // "Join" immediately, without waiting for an MQTT join event.
            modeLabel.setText("Active lobby — join the match");
            actionButton.setText("Join the Lobby");
            actionButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
            infoLabel.setText("Press 'Join' to enter the active lobby.");
            fetchActiveLobbySession(state.gameId());
        }

        // Subscribe to lobby MQTT events for real-time participant updates.
        // Use the buildingId from the server's GameStateDto (not the local default)
        // to ensure the subscription topic matches the server's publish topic.
        if (mqttAdapter != null && mqttAdapter.isConnected()) {
            // Clean up any subscription from a previous visit before
            // creating new ones, otherwise old topics for a different
            // game machine would keep delivering messages.
            cleanupSubscriptions();

            String mqttBuildingId = state.buildingId() != null ? state.buildingId() : buildingId;
            // Track the exact topic filters so cleanupSubscriptions()
            // can unsubscribe later.
            lobbyTopicFilter = "building/" + mqttBuildingId + "/game/" + state.gameId() + "/session/lobby/+";
            stateTopicFilter = "building/" + mqttBuildingId + "/game/" + state.gameId() + "/state";

            StateSubscriber subscriber = new StateSubscriber(mqttAdapter, mqttBuildingId,
                    (topic, payload) -> Platform.runLater(() -> handleLobbyMqttMessage(topic, payload)));
            subscriber.subscribeToLobbyEvents(state.gameId());

            // Also subscribe to the game-machine state topic so we detect
            // another player creating a lobby on the same machine while we
            // are still in creator mode (game was AVAILABLE when we opened
            // this view but has since transitioned to LOBBY).  Without this,
            // a second client that opened the lobby view before the first
            // created the lobby would still show "Crea Lobby" and could
            // attempt a redundant create that the server would reject.
            StateSubscriber stateSubscriber = new StateSubscriber(mqttAdapter, mqttBuildingId,
                    (topic, payload) -> Platform.runLater(() -> handleStateMqttMessage(topic, payload)));
            stateSubscriber.subscribeToStates(state.gameId());
            subscribed = true;
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
        com.gameplatform.client.infrastructure.rest.ApiClient.instance()
                .get("/api/sessions/lobby/active", "gameId=" + gameId, GameSessionDto.class)
                .thenAccept(session -> Platform.runLater(() -> {
                    if (session == null) {
                        infoLabel.setText("Empty lobby.");
                        return;
                    }
                    // 200: lobby session found — populate state.
                    lobbySessionId = session.id();
                    infoLabel.setText("Active lobby found. Press 'Join' to enter.");
                    participants.clear();
                    if (session.participants() != null) {
                        for (String p : session.participants()) {
                            if (p != null && !p.isBlank() && !participants.contains(p)) participants.add(p);
                        }
                    }
                    refreshParticipantsBox();
                }))
                .exceptionally(ex -> { Platform.runLater(() -> {
                    Throwable t = ex;
                    while (t.getCause() != null) t = t.getCause();
                    if (t instanceof ServerUnavailableException
                            || t instanceof RuntimeException
                                && t.getMessage() != null && t.getMessage().contains("HTTP 404")) {
                        // No active lobby session — fall back to creator mode so the
                        // user can create a fresh lobby. The server's
                        // createLobby() handles any stale LOBBY status by
                        // releasing the game first.
                        fallbackToCreatorMode();
                        return;
                    }
                    infoLabel.setText("Unable to retrieve the lobby: " + t.getMessage());
                }); return null; });
    }

    // ─────────────────────────── Button handlers ──────────────────────────────

    /**
     * Identity the server should record as the lobby creator / session
     * participant. For single-player games (maxPlayers == 1) the user id
     * (UUID) is returned so the Central player read-models key statistics
     * on the user id (matching {@code /api/players/me/statistics}); for
     * multiplayer games the username is returned to preserve the existing
     * lobby echo / turn-sync display contract (see known-limit in the
     * project report). Falls back to the username when the user id has not
     * been resolved.
     */
    private String serverIdentityForLobby() {
        if (currentGame != null && currentGame.maxPlayers() == 1
                && currentUserId != null && !currentUserId.isBlank()) {
            return currentUserId;
        }
        return currentUsername;
    }

    private void handleActionButton() {
        if (currentGame == null) return;

        if (creatorMode && lobbySessionId == null) {
            // CREATE LOBBY — only the first player (game was AVAILABLE at
            // selection time) and only before the server has confirmed the
            // lobby session id.
            this.lobbyCreateInitiated = true;
            if (sessionPublisher != null) {
                sessionPublisher.publishLobbyCreate(currentGame.gameId(), currentGame.gameType(), serverIdentityForLobby());
            }
            participants.clear();
            participants.add(currentUsername);
            boolean singlePlayer = currentGame.maxPlayers() == 1;
            if (singlePlayer) {
                infoLabel.setText("Starting match...");
            } else {
                int minPlayers = currentGame.minPlayers();
                int maxPlayers = currentGame.maxPlayers();
                infoLabel.setText("Lobby created! Waiting for other players (min " + minPlayers
                        + ", max " + maxPlayers + ")...");
                startButton.setVisible(true);
                startButton.setDisable(true); // enabled when enough players join via MQTT
            }
            actionButton.setDisable(true);
            refreshParticipantsBox();
        } else {
            // JOIN LOBBY — a joiner must have a lobbySessionId resolved by
            // fetchActiveLobbySession.  If the REST lookup hasn't returned
            // yet, do NOT fall back to creating a new lobby (which would
            // fail on the server because the game is already in LOBBY);
            // instead ask the user to wait a moment and re-enable the
            // button so they can retry.
            if (lobbySessionId == null) {
                infoLabel.setText("Lobby session not available yet — wait a moment and try again.");
                actionButton.setDisable(false);
                return;
            }
            if (sessionPublisher != null) {
                sessionPublisher.publishLobbyJoin(currentGame.gameId(), lobbySessionId, currentUsername);
            }
            actionButton.setDisable(true);
        }
    }

    private void startLobby() {
        if (sessionPublisher != null && lobbySessionId != null) {
            sessionPublisher.publishLobbyStart(currentGame.gameId(), lobbySessionId);
            infoLabel.setText("Match started!");
            startButton.setDisable(true);
        }
    }

    /**
     * Back button handler.
     * <p>
     * If this client is the creator of an active lobby, the lobby is
     * cancelled on the server (the session is aborted and the game
     * machine is released back to AVAILABLE) before navigating back.
     * If the lobby/create echo has not yet arrived (race condition),
     * a REST fallback cancels the lobby by gameId.
     */
    private void handleBackButton() {
        cleanupSubscriptions();
        if (isCreatorOfActiveLobby()) {
            try {
                if (lobbySessionId != null) {
                    // Normal path: we have the session id, cancel via MQTT.
                    sessionPublisher.publishLobbyCancel(currentGame.gameId(), lobbySessionId, serverIdentityForLobby());
                } else {
                    // Race condition: the lobby/create echo hasn't arrived
                    // yet so we don't have lobbySessionId.  Use a REST
                    // fallback to cancel the active lobby by gameId —
                    // otherwise the lobby would stay stuck until the
                    // LobbyExpirationService timer kicks in.
                    cancelLobbyByGameViaRest(currentGame.gameId());
                }
            } catch (Exception ignored) {
                // Best-effort cancel; navigate back regardless
            }
        } else if (hasJoinedLobby()) {
            // JOINER back-button: publish lobby/leave so the server removes
            // this user from the participants list and the remaining players
            // see them disappear from the list (root-cause fix for the
            // "joiner goes back but stays in the lobby" bug).
            // Guard: only publish leave if this client actually joined the
            // lobby (its username is in the local participants list, which
            // is populated by the server's join echo). A joiner who only
            // opened the lobby view but never pressed "Join" must NOT
            // publish leave (they are not on the server's participants
            // list, so a leave would be a spurious no-op event).
            try {
                if (lobbySessionId != null && sessionPublisher != null) {
                    sessionPublisher.publishLobbyLeave(currentGame.gameId(), lobbySessionId, serverIdentityForLobby());
                }
            } catch (Exception ignored) {
                // Best-effort leave; navigate back regardless
            }
        }
        if (onCancel != null) onCancel.run();
    }

    /**
     * REST fallback to cancel the active lobby by game machine id.
     * Used when the creator navigated back before the MQTT
     * {@code lobby/create} echo arrived (so {@code lobbySessionId}
     * is still null).  Calls
     * {@code POST /api/sessions/lobby/cancel-by-game?gameId=...}
     * with the creator's userId.  Fire-and-forget on a background
     * thread so the UI navigates immediately.
     */
    private void cancelLobbyByGameViaRest(String gameId) {
        new Thread(() -> {
            try {
                String body = "{\"userId\":\"" + serverIdentityForLobby() + "\"}";
                com.gameplatform.client.infrastructure.rest.ApiClient.instance()
                        .post("/api/sessions/lobby/cancel-by-game?gameId=" + gameId, body, GameSessionDto.class)
                        .thenAccept(session -> {
                            if (session != null && session.id() != null && sessionPublisher != null) {
                                sessionPublisher.publishLobbyCancel(gameId, session.id(), serverIdentityForLobby());
                            }
                        });
            } catch (Exception ignored) {}
        }, "cancel-lobby-rest").start();
    }

    /**
     * Determines whether this client may cancel the lobby on back navigation:
     * the creator who has either a confirmed session id OR has at least
     * initiated the create (race condition: the lobby/create echo may not
     * have arrived yet).
     */
    private boolean isCreatorOfActiveLobby() {
        if (!creatorMode || currentGame == null || sessionPublisher == null) {
            return false;
        }
        // Allow cancellation when we have a confirmed session id, OR when
        // we initiated the create (lobbyCreateInitiated=true) even if the
        // echo hasn't arrived yet — in the latter case handleBackButton
        // will use a REST fallback to cancel by gameId.
        if (lobbySessionId == null && !lobbyCreateInitiated) {
            return false;
        }
        if (participants.isEmpty() || !participants.get(0).equals(currentUsername)) {
            // If participants is empty (race: lobby/create echo not arrived),
            // trust lobbyCreateInitiated — the creator is the first participant.
            if (lobbyCreateInitiated && participants.isEmpty()) {
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Determines whether this client may publish a {@code lobby/leave}
     * event on back navigation.  Returns true only when this is a JOINER
     * (creatorMode==false) that has actually joined the lobby: the local
     * participants list (populated by the server's {@code lobby/join}
     * echo via the {@code case "join"} handler, and by
     * {@code fetchActiveLobbySession} on joiner-mode entry) contains
     * this client's username.  This guard is essential to avoid
     * publishing spurious {@code lobby/leave} events when a joiner only
     * opened the lobby view but never pressed "Join" (they are not on
     * the server's participants list, so a leave would be wasted noise).
     */
    private boolean hasJoinedLobby() {
        return !creatorMode
                && lobbySessionId != null
                && participants.contains(currentUsername);
    }

    // ─────────────────────────── MQTT ─────────────────────────────────────────

    /**
     * Unsubscribes from the MQTT topics created in {@link #configure}
     * (lobby events + game-machine state).  Must be called whenever the
     * user navigates away from the lobby view — either back to game
     * selection or forward to GamePlayView after the lobby starts.
     * Without this, stale subscriptions would keep delivering lobby
     * events from OTHER players' sessions (the "slot machine opens
     * unexpectedly for A when B starts a game" bug — affects ALL
     * games, not just slot machines).
     */
    private void cleanupSubscriptions() {
        if (mqttAdapter != null && subscribed) {
            if (lobbyTopicFilter != null) {
                try {
                    mqttAdapter.unsubscribe(lobbyTopicFilter);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            if (stateTopicFilter != null) {
                try {
                    mqttAdapter.unsubscribe(stateTopicFilter);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
        lobbyTopicFilter = null;
        stateTopicFilter = null;
        subscribed = false;
    }

    /**
     * Falls back from joiner mode to creator mode. Used when the game
     * machine is in a stale LOBBY status but no active lobby session
     * exists (REST 404) — the user should be able to create a new
     * lobby instead of being stuck with a disabled "Unisciti" button.
     */
    private void fallbackToCreatorMode() {
        creatorMode = true;
        boolean singlePlayer = currentGame != null && currentGame.maxPlayers() == 1;
        if (singlePlayer) {
            modeLabel.setText("Single-player game");
            actionButton.setText("▶  Play!");
            actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
            infoLabel.setText("No active lobby. Press 'Play' to start the match right away.");
        } else {
            modeLabel.setText("You are the first player — create the lobby");
            actionButton.setText("Create Lobby");
            actionButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
            infoLabel.setText("No active lobby. Create a new lobby to start.");
        }
        actionButton.setDisable(false);
        startButton.setVisible(false);
        startButton.setDisable(true);
        participants.clear();
        refreshParticipantsBox();
    }

    /**
     * Handles game-machine state updates received via MQTT. When this
     * client is in creator mode but another player has already created
     * a lobby on the same machine (game transitioned AVAILABLE →
     * LOBBY), it downgrades itself to joiner mode so the user can join
     * the existing lobby instead of attempting a redundant create.
     *
     * <p>The server publishes a lightweight {@link GameStatePayload}
     * ({@code {gameId, status, userId}}) on the state topic — NOT a
     * full {@link GameStateDto}.  We deserialize the correct type and
     * merge only the status into the cached {@code currentGame},
     * preserving name/gameType/minPlayers/maxPlayers/buildingId from
     * the initial REST load.</p>
     */
    private void handleStateMqttMessage(String topic, byte[] payload) {
        try {
            GameStatePayload stateMsg = MqttPayloadSerializer.deserialize(payload, GameStatePayload.class);
            if (currentGame == null || !stateMsg.gameId().equals(currentGame.gameId())) {
                return;
            }
            // Merge only the status — keep the rest of the cached DTO.
            currentGame = new GameStateDto(
                    currentGame.gameId(),
                    currentGame.gameType(),
                    currentGame.name(),
                    currentGame.buildingId(),
                    stateMsg.status(),
                    currentGame.minPlayers(),
                    currentGame.maxPlayers()
            );

            if (creatorMode && !lobbyCreateInitiated
                    && stateMsg.status() == GameMachineStatus.LOBBY
                    && lobbySessionId == null) {
                // Another player created the lobby while we were still
                // showing "Crea Lobby" (we did NOT initiate the create
                // ourselves). Downgrade to joiner and fetch the active
                // session id so the user can press "Unisciti".
                //
                // The lobbyCreateInitiated guard is essential: the server
                // publishes state=LOBBY BEFORE the lobby/create echo, so
                // without it the creator would receive their own state
                // update and be downgraded — losing the "Avvia Partita"
                // button and wrongly seeing "Unisciti".
                downgradeToJoiner();
            }
        } catch (Exception ignored) {
            // Ignore malformed state payloads
        }
    }

    /**
     * Switches the view from creator mode to joiner mode, refreshing
     * labels and fetching the active lobby session id from the server.
     */
    private void downgradeToJoiner() {
        creatorMode = false;
        participants.clear();
        startButton.setVisible(false);
        startButton.setDisable(true);

        modeLabel.setText("Active lobby — join the match");
        actionButton.setText("Join the Lobby");
        actionButton.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 10 28; -fx-background-radius: 6;");
        actionButton.setDisable(false);
        infoLabel.setText("A lobby has already been created by another player. Press 'Join' to enter.");

        if (currentGame != null) {
            fetchActiveLobbySession(currentGame.gameId());
        }
        refreshParticipantsBox();
    }

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
                            infoLabel.setText("Lobby confirmed (ID: " + lobbySessionId.substring(0, 8)
                                    + "...). Waiting for players (" + currentSize + "/" + minPlayers + ").");
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
                            infoLabel.setText(joined + " joined the lobby. (" + participants.size()
                                    + " players) — You can start the match!");
                        } else {
                            infoLabel.setText(joined + " joined the lobby. (" + participants.size()
                                    + "/" + minPlayers + " minimum players)");
                        }
                    }
                }
                case "leave" -> {
                    // A participant (joiner) left the lobby.  Remove them from
                    // the local participants list so the UI updates in real
                    // time.  Idempotent: if the leaving user was already
                    // removed (QoS-1 redelivery) the remove is a no-op.
                    LobbyLeavePayload leavePayload = MqttPayloadSerializer.deserialize(payload, LobbyLeavePayload.class);
                    String left = leavePayload.userId();
                    if (left != null) {
                        participants.remove(left);
                        refreshParticipantsBox();
                        int minPlayers = currentGame != null ? currentGame.minPlayers() : 1;
                        if (participants.size() >= minPlayers) {
                            startButton.setDisable(false);
                            infoLabel.setText(left + " left the lobby. (" + participants.size() + " players)");
                        } else {
                            startButton.setDisable(true);
                            infoLabel.setText(left + " left the lobby. (" + participants.size()
                                    + "/" + minPlayers + " minimum players)");
                        }
                    }
                }
                case "start" -> {
                    // Server confirmed start — fire the callback to navigate
                    // to GamePlayView.  Clean up subscriptions FIRST so we
                    // don't receive any more lobby events (e.g. a future
                    // cancel from a different session) while the game is
                    // running or after the user navigates away.
                    cleanupSubscriptions();
                    if (onLobbyStarted != null && currentGame != null) {
                        onLobbyStarted.accept(currentGame, lobbySessionId, new ArrayList<>(participants));
                    }
                }
                case "cancel" -> {
                    // The creator (or the lobby expiration timer) closed the
                    // lobby.  Auto-navigate every joiner back to game
                    // selection so the lobby is closed for ALL users, not
                    // just locally informed — root-cause fix for the
                    // "creator backs out but joiners stay stuck on the
                    // lobby screen" bug.  cleanupSubscriptions() first to
                    // avoid stale MQTT callbacks after navigation.
                    cleanupSubscriptions();
                    lobbySessionId = null;
                    if (onCancel != null) {
                        onCancel.run();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors silently
        }
    }

    private void refreshParticipantsBox() {
        participantsBox.getChildren().clear();
        if (participants.isEmpty()) {
            Label empty = new Label("No players yet");
            empty.setStyle("-fx-text-fill: #666; -fx-font-size: 12;");
            participantsBox.getChildren().add(empty);
        } else {
            for (int i = 0; i < participants.size(); i++) {
                String p = participants.get(i);
                String icon = (i == 0) ? "👑 " : "👤 ";
                Label l = new Label(icon + p + (p.equals(currentUsername) ? "  (you)" : ""));
                l.setStyle("-fx-text-fill: " + (i == 0 ? "#f1c40f" : "#ddd") + "; -fx-font-size: 13;");
                participantsBox.getChildren().add(l);
            }
        }
    }
}
