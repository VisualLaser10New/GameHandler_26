package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.shared.dto.SignupRequestDto;
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
 * JavaFX view for user registration.
 * <p>
 * Displays a signup form with username, email, and password fields. Inputs are
 * validated and sent to the Local Server via an HTTP POST request to
 * {@code /api/auth/signup}.
 */
public class SignupView {
    private final VBox root;
    private final TextField usernameField;
    private final TextField emailField;
    private final PasswordField passwordField;
    private final Label errorLabel;
    private final Button signupButton;
    private Runnable onSignupSuccess;
    private Runnable onCancel;
    private final String inputFieldStyle;

    public SignupView() {
        inputFieldStyle = "-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;";

        root = new VBox(12);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 40; -fx-background-color: #1e1e1e;");

        Label title = new Label("Create Account");
        title.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #eee;");

        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(260);
        usernameField.setStyle(inputFieldStyle);

        emailField = new TextField();
        emailField.setPromptText("Email Address");
        emailField.setMaxWidth(260);
        emailField.setStyle(inputFieldStyle);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(260);
        passwordField.setStyle(inputFieldStyle);

        signupButton = new Button("Sign Up");
        signupButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 24; -fx-background-radius: 4;");

        Hyperlink loginLink = new Hyperlink("Already have an account? Log In");
        loginLink.setStyle("-fx-text-fill: #3498db; -fx-underline: true; -fx-font-size: 13;");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");

        root.getChildren().addAll(title, usernameField, emailField, passwordField, signupButton, loginLink, errorLabel);

        signupButton.setOnAction(e -> performSignup());
        passwordField.setOnAction(e -> performSignup());
        loginLink.setOnAction(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
    }

    @Deprecated
    private String inputFieldStyle() {
        return "-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;";
    }

    public Parent getView() {
        return root;
    }

    public void setOnSignupSuccess(Runnable callback) {
        this.onSignupSuccess = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    public void performSignup() {
        String username = usernameField.getText().strip();
        String email = emailField.getText().strip();
        String password = passwordField.getText();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            errorLabel.setText("All fields are required");
            return;
        }

        signupButton.setDisable(true);
        errorLabel.setText("");

        SignupRequestDto request = new SignupRequestDto(username, password, email);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(request);

            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "https://localhost:8081");
            HttpClient client = com.gameplatform.client.infrastructure.security.HttpClientHelper.getHttpClient(localServerUrl);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(localServerUrl + "/api/auth/signup"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        javafx.application.Platform.runLater(() -> {
                            signupButton.setDisable(false);
                            if (response.statusCode() == 201) {
                                errorLabel.setStyle("-fx-text-fill: #2ecc71;");
                                errorLabel.setText("Signup successful!");
                                if (onSignupSuccess != null) {
                                    onSignupSuccess.run();
                                }
                            } else if (response.statusCode() == 409) {
                                errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                                errorLabel.setText("Username or email already exists");
                            } else {
                                errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                                errorLabel.setText("Signup failed: status " + response.statusCode());
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        javafx.application.Platform.runLater(() -> {
                            signupButton.setDisable(false);
                            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                            errorLabel.setText("Connection error: " + ex.getMessage());
                        });
                        return null;
                    });
        } catch (Exception ex) {
            signupButton.setDisable(false);
            errorLabel.setStyle("-fx-text-fill: #e74c3c;");
            errorLabel.setText("Error: " + ex.getMessage());
        }
    }

    public void reset() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}
