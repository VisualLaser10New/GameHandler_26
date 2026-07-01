package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.config.MqttClientConfig;
import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.ui.components.StatusBarComponent;
import com.gameplatform.shared.dto.GameStateDto;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point and navigation controller.
 * <p>
 * Extends {@link Application} and manages the primary stage with a
 * {@link BorderPane} layout that provides a top navigation bar, a
 * centre area that switches between views (login, game selection,
 * game play, statistics), and a bottom {@link StatusBarComponent}.
 * <p>
 * Navigation flow: {@code login → game_selection → game_play}
 * with direct access to {@code statistics} from the navigation bar.
 * <p>
 * MQTT services are initialised at startup from environment variables:
 * <ul>
 *   <li>{@code MQTT_BROKER_URL} (default: {@code tcp://localhost:1883})</li>
 *   <li>{@code MQTT_CLIENT_ID} (default: {@code game-client})</li>
 *   <li>{@code BUILDING_ID} (default: {@code building-001})</li>
 * </ul>
 */
public class MainView extends Application {
    private static final String VIEW_LOGIN = "login";
    private static final String VIEW_GAME_SELECTION = "game_selection";
    private static final String VIEW_GAME_PLAY = "game_play";
    private static final String VIEW_STATISTICS = "statistics";

    private static final String VIEW_SIGNUP = "signup";

    private Stage primaryStage;
    private BorderPane root;
    private HBox navBar;
    private StatusBarComponent statusBar;
    private Button gamesNavButton;
    private Button statsNavButton;
    private LoginView loginView;
    private SignupView signupView;
    private GameSelectionView gameSelectionView;
    private GamePlayView gamePlayView;
    private StatisticsView statisticsView;

    private MqttClientAdapter mqttAdapter;
    private String buildingId;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Game Client Emulator");

        root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");

        navBar = new HBox(8);
        navBar.setStyle("-fx-padding: 8 12; -fx-background-color: #151515; -fx-border-color: #333;");
        navBar.setAlignment(Pos.CENTER_LEFT);

        gamesNavButton = createNavButton("Games", VIEW_GAME_SELECTION);
        statsNavButton = createNavButton("Statistics", VIEW_STATISTICS);
        navBar.getChildren().addAll(gamesNavButton, statsNavButton);
        root.setTop(navBar);

        statusBar = new StatusBarComponent();
        statusBar.updateStatus("Initializing...");
        root.setBottom(statusBar);

        initializeServices();
        initializeViews();

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();

        navigateTo(VIEW_LOGIN);
        statusBar.updateStatus("Disconnected");
    }

    private Button createNavButton(String text, String viewName) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc; -fx-padding: 4 14; -fx-background-radius: 3;");
        btn.setOnAction(e -> navigateTo(viewName));
        return btn;
    }

    /**
     * Initialises MQTT connectivity using environment variables with
     * sensible defaults.
     */
    private void initializeServices() {
        try {
            String brokerUrl = System.getenv().getOrDefault("MQTT_BROKER_URL", "tcp://localhost:1883");
            String clientId = System.getenv().getOrDefault("MQTT_CLIENT_ID", "game-client");
            buildingId = System.getenv().getOrDefault("BUILDING_ID", "building-001");
            String gameId = System.getenv().getOrDefault("GAME_ID", "game-1");
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "http://localhost:8081");

            // Perform dynamic enrollment if using TLS (ssl://)
            if (brokerUrl.startsWith("ssl://")) {
                statusBar.updateStatus("Enrolling device certificates...");
                com.gameplatform.client.infrastructure.security.CertificateEnrollmentService enrollmentService = 
                        new com.gameplatform.client.infrastructure.security.CertificateEnrollmentService(gameId, localServerUrl);
                boolean enrolled = enrollmentService.enrollIfNecessary();
                if (!enrolled) {
                    statusBar.updateStatus("Enrollment failed. Proceeding without certs.");
                } else {
                    statusBar.updateStatus("Enrollment successful.");
                }
            }

            MqttClientConfig mqttConfig = new MqttClientConfig(brokerUrl, clientId, buildingId);
            mqttAdapter = new MqttClientAdapter(mqttConfig);
            mqttAdapter.connect();
            statusBar.updateStatus("Connected to MQTT");
        } catch (Exception e) {
            statusBar.updateStatus("MQTT Error: " + e.getMessage());
        }
    }

    /**
     * Creates all view instances and wires the navigation callbacks.
     */
    private void initializeViews() {
        gameSelectionView = new GameSelectionView(mqttAdapter, buildingId);
        gamePlayView = new GamePlayView();
        loginView = new LoginView();
        signupView = new SignupView();
        statisticsView = new StatisticsView();

        loginView.setOnLoginSuccess(() -> navigateTo(VIEW_GAME_SELECTION));
        loginView.setOnNavigateToSignup(() -> navigateTo(VIEW_SIGNUP));

        signupView.setOnSignupSuccess(() -> navigateTo(VIEW_LOGIN));
        signupView.setOnCancel(() -> navigateTo(VIEW_LOGIN));

        gameSelectionView.setOnGameSelected((GameStateDto state) -> {
            gamePlayView.setGameState(state);
            navigateTo(VIEW_GAME_PLAY);
        });
    }

    /**
     * Switches the centre area to the requested view.
     * <p>
     * The navigation bar is hidden on the login and game-play views
     * and visible on the game-selection and statistics views.
     *
     * @param viewName one of {@code "login"}, {@code "game_selection"},
     *                 {@code "game_play"}, or {@code "statistics"}
     * @throws IllegalArgumentException for unknown view names
     */
    public void navigateTo(String viewName) {
        if (primaryStage == null) return;

        switch (viewName) {
            case VIEW_LOGIN:
                loginView.reset();
                root.setCenter(loginView.getView());
                navBar.setVisible(false);
                break;
            case VIEW_SIGNUP:
                signupView.reset();
                root.setCenter(signupView.getView());
                navBar.setVisible(false);
                break;
            case VIEW_GAME_SELECTION:
                gameSelectionView.refreshGames();
                root.setCenter(gameSelectionView.getView());
                navBar.setVisible(true);
                break;
            case VIEW_GAME_PLAY:
                root.setCenter(gamePlayView.getView());
                navBar.setVisible(false);
                break;
            case VIEW_STATISTICS:
                statisticsView.showStats();
                root.setCenter(statisticsView.getView());
                navBar.setVisible(true);
                break;
            default:
                throw new IllegalArgumentException("Unknown view: " + viewName);
        }
    }

    /**
     * Disconnects the MQTT adapter on application shutdown.
     */
    private void shutdown() {
        if (mqttAdapter != null) {
            try {
                mqttAdapter.disconnect();
            } catch (Exception e) {
                // ignore during shutdown
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
