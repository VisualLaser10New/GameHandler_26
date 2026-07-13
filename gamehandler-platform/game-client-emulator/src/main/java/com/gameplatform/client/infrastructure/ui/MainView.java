package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.application.service.ConnectionMonitorService;
import com.gameplatform.client.application.service.GameOrchestrationService;
import com.gameplatform.client.application.service.HeartbeatService;
import com.gameplatform.client.application.service.PlayerTournamentFlow;
import com.gameplatform.client.infrastructure.config.MqttClientConfig;
import com.gameplatform.client.infrastructure.mqtt.*;
import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.gameplatform.client.infrastructure.ui.components.StatusBarComponent;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Main JavaFX application entry point and navigation controller (extended
 * in FASE 7 §7.C for routing, dynamic navbar and logout).
 * <p>
 * Delegates the navbar to {@link NavbarController} so the visible buttons
 * reflect {@link HttpClientHelper#getRoles()}. Adds eight new view
 * constants (tournament browsing, player stats/matches, the three admin
 * dashboards and the admin-requests polling view) plus the inherited
 * six (login / signup / game selection / lobby / game play / statistics).
 * On login, the navbar is rebuilt; on logout every session field is
 * cleared and the user is sent back to the login screen.
 */
public class MainView extends Application {

    private Stage primaryStage;
    private BorderPane root;
    private NavbarController navbar;
    private StatusBarComponent statusBar;

    private LoginView loginView;
    private SignupView signupView;
    private GameSelectionView gameSelectionView;
    private LobbyView lobbyView;
    private GamePlayView gamePlayView;
    private StatisticsView statisticsView;
    private MyStatisticsView myStatisticsView;
    private MyMatchesView myMatchesView;
    private TournamentsView tournamentsView;
    private LocalAdminDashboard localAdminDashboard;
    private GameAdminDashboard gameAdminDashboard;
    private PlatformAdminDashboard platformAdminDashboard;
    private AdminRequestsView adminRequestsView;

    private PlayerTournamentFlow playerTournamentFlow;

    private MqttClientAdapter mqttAdapter;
    private SessionPublisher sessionPublisher;
    private GameOrchestrationService orchestrationService;
    private ConnectionMonitorService connectionMonitor;
    private HeartbeatService heartbeatService;
    private MqttConnectionManager connectionManager;

    private String buildingId = "building-1";
    private String gameId = "game-1";

    // ─────────────────────────── JavaFX lifecycle ────────────────────────────

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Game Client Emulator");

        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        navbar = new NavbarController();
        navbar.setOnNavigate(this::navigateTo);
        navbar.setOnLogout(this::doLogout);
        root.setTop(navbar.getNode());

        statusBar = new StatusBarComponent();
        statusBar.updateStatus("Inizializzazione...");
        root.setBottom(statusBar);

        initializeServices();
        initializeViews();

        Scene scene = new Scene(root, 1100, 720);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        navigateTo(NavbarController.VIEW_LOGIN);
    }

    @Override
    public void stop() {
        shutdown();
    }

    // ─────────────────────────── Initialisation ─────────────────────────────

    private void initializeServices() {
        try {
            String brokerUrl = System.getenv().getOrDefault("MQTT_BROKER_URL", MqttClientConfig.DEFAULT_BROKER_URL);
            String clientId  = System.getenv().getOrDefault("MQTT_CLIENT_ID", "game-client");
            buildingId       = System.getenv().getOrDefault("BUILDING_ID", "building-1");
            gameId           = System.getenv().getOrDefault("GAME_ID", "game-1");
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", ApiClient.DEFAULT_BASE_URL);

            if (brokerUrl.startsWith("ssl://")) {
                statusBar.updateStatus("Enrollment certificati...");
                com.gameplatform.client.infrastructure.security.CertificateEnrollmentService enrollmentService =
                        new com.gameplatform.client.infrastructure.security.CertificateEnrollmentService(gameId, localServerUrl);
                boolean enrolled = enrollmentService.enrollIfNecessary();
                statusBar.updateStatus(enrolled ? "Enrollment completato." : "Enrollment fallito, si procede senza cert.");
            }

            MqttClientConfig mqttConfig = new MqttClientConfig(brokerUrl, clientId, buildingId);
            mqttAdapter = new MqttClientAdapter(mqttConfig);
            mqttAdapter.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    Platform.runLater(() -> statusBar.updateStatus("Connesso a MQTT"));
                }
                @Override public void connectionLost(Throwable cause) {
                    String msg = cause != null ? cause.getMessage() : "motivo sconosciuto";
                    Platform.runLater(() -> statusBar.updateStatus("Disconnesso: " + msg));
                }
                @Override public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {}
                @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
            });
            mqttAdapter.connect();
            statusBar.updateStatus("Connesso a MQTT");

            connectionManager = new MqttConnectionManager(mqttAdapter);
            HeartbeatPublisher heartbeatPublisher = new HeartbeatPublisher(mqttAdapter, buildingId);
            sessionPublisher = new SessionPublisher(mqttAdapter, buildingId);
            heartbeatService   = new HeartbeatService(heartbeatPublisher);
            connectionMonitor  = new ConnectionMonitorService(connectionManager, heartbeatService);
            orchestrationService = new GameOrchestrationService(sessionPublisher, connectionMonitor, gameId);

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

    private void initializeViews() {
        loginView        = new LoginView();
        signupView       = new SignupView();
        gameSelectionView = new GameSelectionView(mqttAdapter, buildingId);
        lobbyView        = new LobbyView(sessionPublisher, mqttAdapter, buildingId);
        gamePlayView     = new GamePlayView();
        statisticsView   = new StatisticsView();
        myStatisticsView = new MyStatisticsView();
        myMatchesView    = new MyMatchesView();
        tournamentsView  = new TournamentsView();
        localAdminDashboard   = new LocalAdminDashboard();
        gameAdminDashboard    = new GameAdminDashboard();
        platformAdminDashboard = new PlatformAdminDashboard();
        adminRequestsView      = new AdminRequestsView();
        playerTournamentFlow    = new PlayerTournamentFlow();

        gamePlayView.setOrchestrationService(orchestrationService);
        gamePlayView.setSessionPublisher(sessionPublisher);
        gamePlayView.setGameId(gameId);
        gamePlayView.setMqttContext(mqttAdapter, buildingId);

        // Splash navigation flows between views:
        loginView.setOnLoginSuccess(() -> {
            new Thread(() -> {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    String username = HttpClientHelper.getCurrentUsername();
                    if (username != null) {
                        lobbyView.setCurrentUser(username);
                        gamePlayView.setCurrentUser(username);
                        connectionMonitor.onLoggedIn();
                        statusBar.updateStatus("Connesso come: " + username
                                + "  · ruoli=" + HttpClientHelper.getRoles());
                    }
                    navbar.rebuild();
                    navigateTo(defaultViewAfterLogin());
                });
            }, "post-login-thread").start();
        });
        loginView.setOnNavigateToSignup(() -> navigateTo(NavbarController.VIEW_SIGNUP));

        signupView.setOnSignupSuccess(() -> navigateTo(NavbarController.VIEW_LOGIN));
        signupView.setOnCancel(() -> navigateTo(NavbarController.VIEW_LOGIN));

        gameSelectionView.setOnGameSelected((GameStateDto state) -> {
            lobbyView.configure(state);
            navigateTo(NavbarController.VIEW_LOBBY);
        });

        lobbyView.setOnCancel(() -> navigateTo(NavbarController.VIEW_GAME_SELECTION));
        lobbyView.setOnLobbyStarted((state, sessionId, participants) -> {
            gamePlayView.setFromLobby(state, sessionId, participants);
            navigateTo(NavbarController.VIEW_GAME_PLAY);
        });
        gamePlayView.setOnBackToHome(() -> navigateTo(NavbarController.VIEW_GAME_SELECTION));

        // Tournaments → Admin Requests redirection when registering.
        tournamentsView.setOnNavigate(viewName -> navigateTo(viewName));

        gameAdminDashboard.setOnNavigateToRequests(() -> navigateTo(NavbarController.VIEW_ADMIN_REQUESTS));
        platformAdminDashboard.setOnNavigateToRequests(() -> navigateTo(NavbarController.VIEW_ADMIN_REQUESTS));
    }

    // ─────────────────────────── Navigation ──────────────────────────────────

    /**
     * Switches the centre area to the requested view.
     *
     * @param viewName one of the {@code VIEW_*} constants from {@link NavbarController}
     */
    public void navigateTo(String viewName) {
        if (primaryStage == null) return;
        stopPollers();
        boolean showNavbar = true;
        switch (viewName) {
            case NavbarController.VIEW_LOGIN -> {
                loginView.reset();
                root.setCenter(loginView.getView());
                // Hide the navbar until login+roles resolve (PIANO §7.C)
                navbar.getNode().setVisible(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_SIGNUP -> {
                signupView.reset();
                root.setCenter(signupView.getView());
                navbar.getNode().setVisible(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_GAME_SELECTION -> {
                gameSelectionView.refreshGames();
                root.setCenter(gameSelectionView.getView());
            }
            case NavbarController.VIEW_LOBBY -> {
                root.setCenter(lobbyView.getView());
                navbar.getNode().setVisible(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_GAME_PLAY -> {
                root.setCenter(gamePlayView.getView());
                navbar.getNode().setVisible(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_STATISTICS -> {
                statisticsView.showStats();
                root.setCenter(statisticsView.getView());
            }
            case NavbarController.VIEW_MY_STATISTICS -> {
                myStatisticsView.refresh();
                root.setCenter(myStatisticsView.getView());
            }
            case NavbarController.VIEW_MY_MATCHES -> {
                myMatchesView.refresh();
                root.setCenter(myMatchesView.getView());
            }
            case NavbarController.VIEW_TOURNAMENTS -> {
                tournamentsView.refresh();
                root.setCenter(tournamentsView.getView());
            }
            case NavbarController.VIEW_TOURNAMENT_DETAIL -> {
                // Detail renders inside the Tournaments view (master/detail).
                tournamentsView.refresh();
                root.setCenter(tournamentsView.getView());
            }
            case NavbarController.VIEW_ADMIN_LOCAL -> {
                localAdminDashboard.refreshAll();
                root.setCenter(localAdminDashboard.getView());
            }
            case NavbarController.VIEW_ADMIN_GAME -> {
                gameAdminDashboard.refreshCatalog();
                root.setCenter(gameAdminDashboard.getView());
            }
            case NavbarController.VIEW_ADMIN_PLATFORM -> {
                platformAdminDashboard.refreshAll();
                root.setCenter(platformAdminDashboard.getView());
            }
            case NavbarController.VIEW_ADMIN_REQUESTS -> {
                adminRequestsView.onEnter();
                root.setCenter(adminRequestsView.getView());
            }
            default -> throw new IllegalArgumentException("Unknown view: " + viewName);
        }
        if (showNavbar) {
            navbar.getNode().setVisible(true);
            navbar.rebuild();
        }
        root.requestLayout();
    }

    /** Stops any active pollers when leaving a long-running view. */
    private void stopPollers() {
        adminRequestsView.onLeave();
    }

    /**
     * Picks the first view the user should land on after login — must be
     * visible for the current role per {@link NavbarController#rebuild()}
     * (otherwise the navbar would offer no matching button).
     */
    private String defaultViewAfterLogin() {
        if (HttpClientHelper.hasRole("PLATFORM_ADMIN")) return NavbarController.VIEW_ADMIN_PLATFORM;
        if (HttpClientHelper.hasRole("GAME_ADMIN"))     return NavbarController.VIEW_ADMIN_GAME;
        if (HttpClientHelper.hasRole("LOCAL_ADMIN"))    return NavbarController.VIEW_ADMIN_LOCAL;
        return NavbarController.VIEW_GAME_SELECTION;
    }

    // ─────────────────────────── Logout ───────────────────────────────────

    private void doLogout() {
        try {
            HttpClientHelper.clearSession();
            statusBar.updateStatus("Logout effettuato");
            navigateTo(NavbarController.VIEW_LOGIN);
        } catch (Exception e) {
            statusBar.updateStatus("Logout error: " + e.getMessage());
        }
    }

    private void shutdown() {
        try {
            adminRequestsView.onLeave();
            if (connectionMonitor != null) connectionMonitor.stop();
            if (heartbeatService  != null) heartbeatService.stopHeartbeat();
            if (mqttAdapter != null)       mqttAdapter.disconnect();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}