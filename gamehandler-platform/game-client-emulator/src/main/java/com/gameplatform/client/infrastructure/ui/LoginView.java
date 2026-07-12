package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.gameplatform.shared.dto.LoginRequestDto;
import com.gameplatform.shared.dto.LoginResponseDto;
import com.gameplatform.shared.dto.UserInfoDto;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/**
 * JavaFX view for user authentication (PIANO §7.C line 728).
 * <p>
 * Submits a {@link LoginRequestDto} to {@code POST /api/auth/login}
 * through the centralised {@link ApiClient}; on success it then calls
 * {@code GET /api/auth/me} and stores the returned {@link UserInfoDto}
 * enriched payload (token, username, roles, buildings) back into
 * {@link HttpClientHelper} so the role-aware navbar can rebuild.
 */
public class LoginView {
    private final VBox root;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label errorLabel;
    private final Button loginButton;
    private final Hyperlink signupLink;
    private Runnable onLoginSuccess;
    private Runnable onNavigateToSignup;
    private final String usernameFieldStyle;

    public LoginView() {
        usernameFieldStyle = "-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4";
        root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #1e1e1e;");

        Label title = new Label("Game Client Emulator");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #eee;");

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(260);
        usernameField.setStyle(usernameFieldStyle);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(260);
        passwordField.setStyle(usernameFieldStyle);

        loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 24; -fx-background-radius: 4;");

        signupLink = new Hyperlink("Don't have an account? Sign Up");
        signupLink.setStyle("-fx-text-fill: #3498db; -fx-underline: true; -fx-font-size: 13;");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");

        root.getChildren().addAll(title, usernameField, passwordField, loginButton, signupLink, errorLabel);

        loginButton.setOnAction(e -> performLogin());
        passwordField.setOnAction(e -> performLogin());
        signupLink.setOnAction(e -> {
            if (onNavigateToSignup != null) onNavigateToSignup.run();
        });
    }

    public Parent getView() {
        return root;
    }

    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    public void setOnNavigateToSignup(Runnable callback) {
        this.onNavigateToSignup = callback;
    }

    /**
     * Validates the form, sends {@code POST /api/auth/login} via {@link ApiClient}
     * and, on 200, sends {@code GET /api/auth/me} to resolve the enriched
     * {@link UserInfoDto} (roles + buildings). Every step is async; UI
     * mutations are marshalled onto the JavaFX Application Thread.
     */
    public void performLogin() {
        String username = usernameField.getText().strip();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Username and password are required");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("");

        LoginRequestDto request = new LoginRequestDto(username, password);
        ApiClient client = ApiClient.instance();
        client.post("/api/auth/login", request, LoginResponseDto.class)
                .thenCompose(loginResponse -> {
                    if (loginResponse == null || loginResponse.token() == null) {
                        throw new RuntimeException("Login response returned no token");
                    }
                    HttpClientHelper.setToken(loginResponse.token());
                    HttpClientHelper.setCurrentUsername(username);
                    // Fetch the enriched UserInfoDto (roles + buildings).
                    return client.get("/api/auth/me", UserInfoDto.class);
                })
                .thenAccept(userInfo -> Platform.runLater(() -> {
                    if (userInfo != null) {
                        HttpClientHelper.setCurrentUsername(userInfo.username());
                        HttpClientHelper.setRoles(userInfo.roles());
                        HttpClientHelper.setBuildings(userInfo.buildings());
                    }
                    errorLabel.setStyle("-fx-text-fill: #2ecc71;");
                    errorLabel.setText("Login successful");
                    loginButton.setDisable(false);
                    if (onLoginSuccess != null) onLoginSuccess.run();
                }))
                .exceptionally(ex -> { Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                    Throwable cause = ex;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof com.gameplatform.client.infrastructure.rest.AuthenticationException) {
                        errorLabel.setText("Credenziali non valide");
                    } else if (cause instanceof com.gameplatform.client.infrastructure.rest.ServerUnavailableException) {
                        errorLabel.setText("Server non raggiungibile: " + cause.getMessage());
                    } else {
                        errorLabel.setText("Login error: " + cause.getMessage());
                    }
                }); return null; });
    }

    public void reset() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}