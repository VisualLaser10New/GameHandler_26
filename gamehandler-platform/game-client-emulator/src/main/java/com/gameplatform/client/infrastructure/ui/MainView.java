package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.ConnectionMonitorService;
import com.gameplatform.client.application.service.GameOrchestrationService;
import com.gameplatform.client.application.service.HeartbeatService;
import com.gameplatform.client.infrastructure.config.MqttClientConfig;
import com.gameplatform.client.infrastructure.mqtt.*;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.gameplatform.client.infrastructure.ui.components.StatusBarComponent;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point and navigation controller.
 * <p>
 * Manages a top navigation bar and a centre area that switches between:
 * {@code login → signup → game_selection → lobby → game_play → statistics}.
 * <p>
 * On startup, initialises the complete MQTT service stack:
 * <ol>
 *   <li>{@link MqttClientAdapter} — low-level MQTT client</li>
 *   <li>{@link MqttConnectionManager} — reconnect loop</li>
 *   <li>{@link HeartbeatPublisher} / {@link HeartbeatService} — keep-alive</li>
 *   <li>{@link SessionPublisher} — session lifecycle events</li>
 *   <li>{@link GameOrchestrationService} — coordinates session with server</li>
 *   <li>{@link ConnectionMonitorService} — tracks client state</li>
 * </ol>
 * All services are injected into the relevant views.
 */
public class MainView extends Application {

    // View names
    private static final String VIEW_LOGIN          = "login";
    private static final String VIEW_SIGNUP         = "signup";
    private static final String VIEW_GAME_SELECTION = "game_selection";
    private static final String VIEW_LOBBY          = "lobby";
    private static final String VIEW_GAME_PLAY      = "game_play";
    private static final String VIEW_STATISTICS     = "statistics";

    // Stage / layout
    private Stage primaryStage;
    private BorderPane root;
    private HBox navBar;
    private StatusBarComponent statusBar;
    private Button gamesNavButton;
    private Button statsNavButton;

    // Views
    private LoginView loginView;
    private SignupView signupView;
    private GameSelectionView gameSelectionView;
    private LobbyView lobbyView;
    private GamePlayView gamePlayView;
    private StatisticsView statisticsView;

    // MQTT / services
    private MqttClientAdapter mqttAdapter;
    private SessionPublisher sessionPublisher;
    private GameOrchestrationService orchestrationService;
    private ConnectionMonitorService connectionMonitor;
    private HeartbeatService heartbeatService;
    private MqttConnectionManager connectionManager;

    private String buildingId = "building-1";
    private String gameId = "game-1";

    // ─────────────────────────── JavaFX lifecycle ─────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Game Client Emulator");

        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        navBar = new HBox(8);
        navBar.setStyle("-fx-padding: 8 12; -fx-background-color: #151515; -fx-border-color: #333; -fx-border-width: 0 0 1 0;");
        navBar.setAlignment(Pos.CENTER_LEFT);

        gamesNavButton = createNavButton("Games", VIEW_GAME_SELECTION);
        statsNavButton  = createNavButton("Statistics", VIEW_STATISTICS);
        navBar.getChildren().addAll(gamesNavButton, statsNavButton);
        root.setTop(navBar);

        statusBar = new StatusBarComponent();
        statusBar.updateStatus("Inizializzazione...");
        root.setBottom(statusBar);

        initializeServices();
        initializeViews();

        Scene scene = new Scene(root, 950, 680);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        navigateTo(VIEW_LOGIN);
    }

    @Override
    public void stop() {
        shutdown();
    }

    // ─────────────────────────── Initialisation ───────────────────────────────

    /**
     * Initialises all MQTT infrastructure services and wires them together.
     */
    private void initializeServices() {
        try {
            String brokerUrl = System.getenv().getOrDefault("MQTT_BROKER_URL", "tcp://localhost:1883");
            String clientId  = System.getenv().getOrDefault("MQTT_CLIENT_ID", "game-client");
            buildingId       = System.getenv().getOrDefault("BUILDING_ID", "building-1");
            gameId           = System.getenv().getOrDefault("GAME_ID", "game-1");
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "https://localhost:8081");

            // mTLS certificate enrollment (only for ssl:// brokers)
            if (brokerUrl.startsWith("ssl://")) {
                statusBar.updateStatus("Enrollment certificati...");
                com.gameplatform.client.infrastructure.security.CertificateEnrollmentService enrollmentService =
                        new com.gameplatform.client.infrastructure.security.CertificateEnrollmentService(gameId, localServerUrl);
                boolean enrolled = enrollmentService.enrollIfNecessary();
                statusBar.updateStatus(enrolled ? "Enrollment completato." : "Enrollment fallito, si procede senza cert.");
            }

            // MQTT adapter + connection manager
            MqttClientConfig mqttConfig = new MqttClientConfig(brokerUrl, clientId, buildingId);
            mqttAdapter = new MqttClientAdapter(mqttConfig);

            mqttAdapter.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Platform.runLater(() -> statusBar.updateStatus("Connesso a MQTT"));
                }
                @Override
                public void connectionLost(Throwable cause) {
                    String msg = cause != null ? cause.getMessage() : "motivo sconosciuto";
                    Platform.runLater(() -> statusBar.updateStatus("Disconnesso: " + msg));
                }
                @Override
                public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {}
                @Override
                public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
            });

            mqttAdapter.connect();
            statusBar.updateStatus("Connesso a MQTT");

            connectionManager = new MqttConnectionManager(mqttAdapter);

            // Publishers
            HeartbeatPublisher heartbeatPublisher = new HeartbeatPublisher(mqttAdapter, buildingId);
            sessionPublisher = new SessionPublisher(mqttAdapter, buildingId);

            // Services
            heartbeatService   = new HeartbeatService(heartbeatPublisher);
            connectionMonitor  = new ConnectionMonitorService(connectionManager, heartbeatService);
            orchestrationService = new GameOrchestrationService(sessionPublisher, connectionMonitor, gameId);

            // Subscribe to session-start confirmations so GameOrchestrationService resolves its future
            StateSubscriber stateSubscriber = new StateSubscriber(mqttAdapter, buildingId, (topic, payload) -> {
                try {
                    String[] tokens = topic.split("/");
                    if (tokens.length >= 6 && "start".equals(tokens[5])) {
                        orchestrationService.onSessionStartConfirmed(payload);
                    }
                } catch (Exception ignored) {}
            });
            stateSubscriber.subscribeToSessionEvents();

            connectionMonitor.start(gameId);
            connectionMonitor.onConnected();

        } catch (Exception e) {
            statusBar.updateStatus("Errore MQTT: " + e.getMessage());
        }
    }

    /**
     * Creates all view instances and wires their navigation callbacks.
     */
    private void initializeViews() {
        // Construct views
        loginView        = new LoginView();
        signupView       = new SignupView();
        gameSelectionView = new GameSelectionView(mqttAdapter, buildingId);
        lobbyView        = new LobbyView(sessionPublisher, mqttAdapter, buildingId);
        gamePlayView     = new GamePlayView();
        statisticsView   = new StatisticsView();

        // Inject services into GamePlayView
        gamePlayView.setOrchestrationService(orchestrationService);
        gamePlayView.setSessionPublisher(sessionPublisher);
        gamePlayView.setGameId(gameId);
        gamePlayView.setMqttContext(mqttAdapter, buildingId);

        // ── Login ──────────────────────────────────────────────────────────────
        loginView.setOnLoginSuccess(() -> {
            // Update username in views after login resolves /api/auth/me
            new Thread(() -> {
                // Give a moment for the async /me call to finish
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    String username = HttpClientHelper.getCurrentUsername();
                    if (username != null) {
                        lobbyView.setCurrentUser(username);
                        gamePlayView.setCurrentUser(username);
                        connectionMonitor.onLoggedIn();
                        statusBar.updateStatus("Connesso come: " + username);
                    }
                    navigateTo(VIEW_GAME_SELECTION);
                });
            }, "post-login-thread").start();
        });
        loginView.setOnNavigateToSignup(() -> navigateTo(VIEW_SIGNUP));

        // ── Signup ─────────────────────────────────────────────────────────────
        signupView.setOnSignupSuccess(() -> navigateTo(VIEW_LOGIN));
        signupView.setOnCancel(() -> navigateTo(VIEW_LOGIN));

        // ── Game Selection → Lobby ─────────────────────────────────────────────
        gameSelectionView.setOnGameSelected((GameStateDto state) -> {
            lobbyView.configure(state);
            navigateTo(VIEW_LOBBY);
        });

        // ── Lobby → GamePlay or back ────────────────────────────────────────────
        lobbyView.setOnCancel(() -> navigateTo(VIEW_GAME_SELECTION));
        lobbyView.setOnLobbyStarted((state, sessionId, participants) -> {
            gamePlayView.setFromLobby(state, sessionId, participants);
            navigateTo(VIEW_GAME_PLAY);
        });

        // ── GamePlay → back to home after match ends ────────────────────────────
        gamePlayView.setOnBackToHome(() -> navigateTo(VIEW_GAME_SELECTION));
    }

    // ─────────────────────────── Navigation ───────────────────────────────────

    /**
     * Switches the centre area to the requested view.
     *
     * @param viewName one of the {@code VIEW_*} constants
     * @throws IllegalArgumentException for unknown view names
     */
    public void navigateTo(String viewName) {
        if (primaryStage == null) return;
        switch (viewName) {
            case VIEW_LOGIN -> {
                loginView.reset();
                root.setCenter(loginView.getView());
                navBar.setVisible(false);
            }
            case VIEW_SIGNUP -> {
                signupView.reset();
                root.setCenter(signupView.getView());
                navBar.setVisible(false);
            }
            case VIEW_GAME_SELECTION -> {
                gameSelectionView.refreshGames();
                root.setCenter(gameSelectionView.getView());
                navBar.setVisible(true);
            }
            case VIEW_LOBBY -> {
                root.setCenter(lobbyView.getView());
                navBar.setVisible(false);
            }
            case VIEW_GAME_PLAY -> {
                root.setCenter(gamePlayView.getView());
                navBar.setVisible(false);
            }
            case VIEW_STATISTICS -> {
                statisticsView.showStats();
                root.setCenter(statisticsView.getView());
                navBar.setVisible(true);
            }
            default -> throw new IllegalArgumentException("Unknown view: " + viewName);
        }
    }

    // ─────────────────────────── Helpers ──────────────────────────────────────

    private Button createNavButton(String text, String viewName) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc; -fx-padding: 4 14; -fx-background-radius: 3;");
        btn.setOnAction(e -> navigateTo(viewName));
        return btn;
    }

    /** Gracefully disconnects MQTT and stops background services. */
    private void shutdown() {
        try {
            if (connectionMonitor != null) connectionMonitor.stop();
            if (heartbeatService  != null) heartbeatService.stopHeartbeat();
            if (mqttAdapter != null)       mqttAdapter.disconnect();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}
