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
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Punto di ingresso principale dell'applicazione JavaFX e controller di navigazione.
 * <p>
 * Delega la navbar a {@link NavbarController} in modo che i pulsanti
 * visibili riflettano {@link HttpClientHelper#getRoles()}. Gestisce
 * quindici viste (login, signup, selezione giochi, lobby, gioco,
 * statistiche, statistiche personali, match personali, tornei,
 * tre dashboard admin e vista richieste admin). Al login la navbar
 * viene ricostruita; al logout la sessione viene cancellata e
 * l'utente reindirizzato al login.
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

    /**
     * Avvia l'applicazione JavaFX.
     * <p>
     * Inizializza la finestra principale, la navbar, la barra di stato,
     * i servizi (MQTT, orchestrazione) e le viste. Configura i callback
     * di navigazione tra le viste e mostra la schermata di login.
     *
     * @param stage lo stage primario dell'applicazione; non null
     */
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
        statusBar.updateStatus("Initializing...");
        root.setBottom(statusBar);

        initializeServices();
        initializeViews();

        Scene scene = new Scene(root, 1100, 720);
        java.net.URL darkCss = MainView.class.getResource("/styles/dark-theme.css");
        if (darkCss != null) {
            scene.getStylesheets().add(darkCss.toExternalForm());
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        navigateTo(NavbarController.VIEW_LOGIN);
    }

    /**
     * Arresta l'applicazione e libera le risorse.
     */
    @Override
    public void stop() {
        shutdown();
    }

    // ─────────────────────────── Initialisation ─────────────────────────────

    /**
     * Inizializza i servizi di backend.
     * <p>
     * Configura la connessione MQTT, il publisher di heartbeat, il
     * servizio di orchestrazione del gioco, il monitor di connessione
     * e la sottoscrizione agli eventi di sessione.
     * In caso di errore MQTT, aggiorna la barra di stato.
     */
    private void initializeServices() {
        try {
            String brokerUrl = System.getenv().getOrDefault("MQTT_BROKER_URL", MqttClientConfig.DEFAULT_BROKER_URL);
            String clientId  = System.getenv().getOrDefault("MQTT_CLIENT_ID", "game-client");
            buildingId       = System.getenv().getOrDefault("BUILDING_ID", "building-1");
            gameId           = System.getenv().getOrDefault("GAME_ID", "game-1");
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", ApiClient.DEFAULT_BASE_URL);

            if (brokerUrl.startsWith("ssl://")) {
                statusBar.updateStatus("Certificate enrollment...");
                com.gameplatform.client.infrastructure.security.CertificateEnrollmentService enrollmentService =
                        new com.gameplatform.client.infrastructure.security.CertificateEnrollmentService(gameId, localServerUrl);
                boolean enrolled = enrollmentService.enrollIfNecessary();
                statusBar.updateStatus(enrolled ? "Enrollment completed." : "Enrollment failed, proceeding without certificates.");
            }

            MqttClientConfig mqttConfig = new MqttClientConfig(brokerUrl, clientId, buildingId);
            mqttAdapter = new MqttClientAdapter(mqttConfig);
            mqttAdapter.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    Platform.runLater(() -> statusBar.updateStatus("Connected to MQTT"));
                }
                @Override public void connectionLost(Throwable cause) {
                    String msg = cause != null ? cause.getMessage() : "unknown reason";
                    Platform.runLater(() -> statusBar.updateStatus("Disconnected: " + msg));
                }
                @Override public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {}
                @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
            });
            mqttAdapter.connect();
            statusBar.updateStatus("Connected to MQTT");

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
            statusBar.updateStatus("MQTT error: " + e.getMessage());
        }
    }

    /**
     * Inizializza tutte le viste dell'applicazione.
     * <p>
     * Crea le istanze di ogni vista, configura i callback di
     * navigazione, i flussi di login/signup, la selezione del
     * gioco, la lobby, il gameplay e i tornei.
     */
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
                    String userId = HttpClientHelper.getCurrentUserId();
                    if (username != null) {
                        lobbyView.setCurrentUser(username);
                        gamePlayView.setCurrentUser(username);
                        if (userId != null) {
                            lobbyView.setCurrentUserId(userId);
                            gamePlayView.setCurrentUserId(userId);
                        }
                        connectionMonitor.onLoggedIn();
                        statusBar.updateStatus("Connected as: " + username
                                + "  · roles=" + HttpClientHelper.getRoles());
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

        // Tournament match started → swap to the GamePlay view (mirrors the
        // lobby path LobbyView.setOnLobbyStarted → gamePlayView.setFromLobby
        // + navigateTo(VIEW_GAME_PLAY)) so the user can play the freshly
        // created session and end it with a winner for the bracket to advance.
        // The tournament match start happens synchronously via REST and skips
        // the lobby hop, so setFromTournamentMatch is used (vs setFromLobby)
        // for the appropriate status label.
        tournamentsView.setOnMatchStarted((state, sessionId, participants) -> {
            gamePlayView.setFromTournamentMatch(state, sessionId, participants);
            navigateTo(NavbarController.VIEW_GAME_PLAY);
        });

        gameAdminDashboard.setOnNavigateToRequests(() -> navigateTo(NavbarController.VIEW_ADMIN_REQUESTS));
        platformAdminDashboard.setOnNavigateToRequests(() -> navigateTo(NavbarController.VIEW_ADMIN_REQUESTS));
    }

    // ─────────────────────────── Navigation ──────────────────────────────────

    /**
     * Cambia la vista centrale dell'applicazione.
     * <p>
     * Arresta i poller attivi, imposta la vista richiesta nell'area
     * centrale del layout e aggiorna la visibilità della navbar.
     * Le viste di login, signup, lobby e gioco nascondono la navbar.
     *
     * @param viewName una delle costanti {@code VIEW_*} definite in
     *                 {@link NavbarController}
     * @throws IllegalArgumentException se il nome della vista non è riconosciuto
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
                navbar.getNode().setManaged(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_SIGNUP -> {
                signupView.reset();
                root.setCenter(signupView.getView());
                navbar.getNode().setVisible(false);
                navbar.getNode().setManaged(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_GAME_SELECTION -> {
                gameSelectionView.refreshGames();
                root.setCenter(gameSelectionView.getView());
            }
            case NavbarController.VIEW_LOBBY -> {
                root.setCenter(lobbyView.getView());
                navbar.getNode().setVisible(false);
                navbar.getNode().setManaged(false);
                showNavbar = false;
            }
            case NavbarController.VIEW_GAME_PLAY -> {
                root.setCenter(gamePlayView.getView());
                navbar.getNode().setVisible(false);
                navbar.getNode().setManaged(false);
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
            navbar.getNode().setManaged(true);
            navbar.rebuild();
        }
        root.requestLayout();
    }

    /**
     * Arresta i poller attivi quando si abbandona una vista a lunga esecuzione.
     */
    private void stopPollers() {
        adminRequestsView.onLeave();
    }

    /**
     * Seleziona la vista predefinita dopo il login in base al ruolo.
     * <p>
     * La vista scelta deve essere visibile per il ruolo corrente secondo
     * {@link NavbarController#rebuild()}, altrimenti la navbar non
     * mostrerebbe alcun pulsante corrispondente.
     *
     * @return il nome della vista predefinita per il ruolo corrente
     */
    private String defaultViewAfterLogin() {
        if (HttpClientHelper.hasRole(Role.PLATFORM_ADMIN.name())) return NavbarController.VIEW_ADMIN_PLATFORM;
        if (HttpClientHelper.hasRole(Role.GAME_ADMIN.name()))     return NavbarController.VIEW_ADMIN_GAME;
        if (HttpClientHelper.hasRole(Role.LOCAL_ADMIN.name()))    return NavbarController.VIEW_ADMIN_LOCAL;
        return NavbarController.VIEW_GAME_SELECTION;
    }

    // ─────────────────────────── Logout ───────────────────────────────────

    /**
     * Esegue il logout dell'utente.
     * <p>
     * Cancella i dati di sessione da {@link HttpClientHelper}, reimposta
     * l'URL base del client API e naviga alla schermata di login.
     */
    private void doLogout() {
        try {
            HttpClientHelper.clearSession();
            ApiClient.instance().setBaseUrl(ApiClient.DEFAULT_BASE_URL);
            statusBar.updateStatus("Logout completed");
            navigateTo(NavbarController.VIEW_LOGIN);
        } catch (Exception e) {
            statusBar.updateStatus("Logout error: " + e.getMessage());
        }
    }

    /**
     * Arresta tutti i servizi e libera le risorse.
     * <p>
     * Arresta il poller delle richieste admin, il monitor di connessione,
     * il servizio di heartbeat e disconnette l'adattatore MQTT.
     */
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