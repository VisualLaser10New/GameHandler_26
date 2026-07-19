package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
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
 * Vista JavaFX per la registrazione di un nuovo utente.
 * <p>
 * Mostra un form di registrazione con campi per username, email e password.
 * I dati vengono validati e inviati al server locale tramite una richiesta
 * HTTP POST a {@code /api/auth/signup}.
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

    /**
     * Costruisce la vista di registrazione.
     * <p>
     * Inizializza i campi per username, email e password, il pulsante
     * di registrazione, il link per il login e l'etichetta per gli errori.
     */
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

    /**
     * Restituisce lo stile CSS per i campi di input.
     *
     * @return una stringa con lo stile CSS per i campi di input
     * @deprecated il valore è disponibile come costante {@link #inputFieldStyle}
     */
    @Deprecated
    private String inputFieldStyle() {
        return "-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;";
    }

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Registra il callback per la registrazione riuscita.
     *
     * @param callback l'azione da eseguire dopo il signup; può essere null
     */
    public void setOnSignupSuccess(Runnable callback) {
        this.onSignupSuccess = callback;
    }

    /**
     * Registra il callback per l'annullamento e il ritorno al login.
     *
     * @param callback l'azione da eseguire per tornare al login; può essere null
     */
    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    /**
     * Esegue la registrazione con i dati inseriti.
     * <p>
     * Valida che tutti i campi siano compilati, costruisce una
     * richiesta {@link SignupRequestDto} e la invia in modo asincrono
     * a {@code /api/auth/signup}. Gestisce i codici di risposta
     * 201 (successo), 409 (conflitto) e 400 (errore di validazione).
     */
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

            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", ApiClient.DEFAULT_BASE_URL);
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
                            } else if (response.statusCode() == 400) {
                                errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                                errorLabel.setText("Data validation error");
                            }
                            else {
                                errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                                errorLabel.setText("Signup failed: status " + response.statusCode() + "\nWhy? Because " + response);
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

    /**
     * Resetta il form di registrazione allo stato iniziale.
     * <p>
     * Pulisce i campi username, email e password e reimposta
     * l'etichetta di errore al colore e testo predefiniti.
     */
    public void reset() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}
