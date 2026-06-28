package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.shared.dto.LoginRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * JavaFX view for user authentication.
 * <p>
 * Displays a login form with username and password fields. Credentials are
 * sent to the Local Server via an asynchronous HTTP POST to
 * {@code /api/auth/login}. On success the {@code onLoginSuccess} callback
 * is invoked; on failure an error message is shown below the form.
 */
public class LoginView {
    private final VBox root;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final Label errorLabel;
    private final Button loginButton;
    private Runnable onLoginSuccess;

    public LoginView() {
        root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #1e1e1e;");

        Label title = new Label("Game Client Emulator");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #eee;");

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(260);
        usernameField.setStyle(usernameFieldStyle());

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(260);
        passwordField.setStyle(usernameFieldStyle());

        loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 24; -fx-background-radius: 4;");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");

        root.getChildren().addAll(title, usernameField, passwordField, loginButton, errorLabel);

        loginButton.setOnAction(e -> performLogin());
        passwordField.setOnAction(e -> performLogin());
    }

    private String usernameFieldStyle() {
        return "-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;";
    }

    /**
     * Returns the root JavaFX node for this view.
     *
     * @return the login form's {@link Parent} node
     */
    public Parent getView() {
        return root;
    }

    /**
     * Registers a callback to be invoked after a successful login.
     *
     * @param callback the action to run on login success (e.g. navigate to
     *                 the game selection view); may be {@code null}
     */
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    /**
     * Validates the form, serialises the credentials as JSON, and sends an
     * asynchronous POST request to the Local Server authentication endpoint.
     * <p>
     * The login button is disabled while the request is in flight. On a
     * {@code 200} response the {@code onLoginSuccess} callback is executed
     * on the JavaFX Application Thread.
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(request);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        javafx.application.Platform.runLater(() -> {
                            loginButton.setDisable(false);
                            if (response.statusCode() == 200) {
                                errorLabel.setStyle("-fx-text-fill: #2ecc71;");
                                errorLabel.setText("Login successful");
                                if (onLoginSuccess != null) {
                                    onLoginSuccess.run();
                                }
                            } else {
                                errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                                errorLabel.setText("Login failed: " + response.statusCode());
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        javafx.application.Platform.runLater(() -> {
                            loginButton.setDisable(false);
                            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                            errorLabel.setText("Connection error: " + ex.getMessage());
                        });
                        return null;
                    });
        } catch (Exception ex) {
            loginButton.setDisable(false);
            errorLabel.setText("Error: " + ex.getMessage());
        }
    }

    /**
     * Clears all input fields and resets the error label to its default
     * (red) styling.
     */
    public void reset() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}
