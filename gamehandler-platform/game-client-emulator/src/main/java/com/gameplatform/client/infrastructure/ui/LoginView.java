package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.domain.exception.AuthenticationException;
import com.gameplatform.client.domain.exception.ServerUnavailableException;
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
 * Vista JavaFX per l'autenticazione dell'utente.
 * <p>
 * Invia una richiesta {@link LoginRequestDto} a {@code POST /api/auth/login}
 * tramite {@link ApiClient}; in caso di successo chiama {@code GET /api/auth/me}
 * e memorizza il payload {@link UserInfoDto} (token, username, ruoli, edifici)
 * in {@link HttpClientHelper} per consentire alla navbar di ricostruirsi
 * in base ai ruoli.
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

    /**
     * Costruisce la vista di login.
     * <p>
     * Inizializza i campi per username e password, il pulsante di login,
     * il link per la registrazione e l'etichetta per gli errori.
     */
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

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Registra il callback per il login riuscito.
     *
     * @param callback l'azione da eseguire dopo il login; può essere null
     */
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }

    /**
     * Registra il callback per la navigazione alla vista di registrazione.
     *
     * @param callback l'azione da eseguire per navigare al signup; può essere null
     */
    public void setOnNavigateToSignup(Runnable callback) {
        this.onNavigateToSignup = callback;
    }

    /**
     * Esegue il login con le credenziali inserite.
     * <p>
     * Valida che username e password non siano vuoti, invia una POST
     * asincrona a {@code /api/auth/login} e, in caso di successo,
     * recupera i dati utente arricchiti tramite {@code GET /api/auth/me}.
     * Ogni mutazione dell'interfaccia avviene sul thread JavaFX Application.
     *
     * @throws AuthenticationException se le credenziali non sono valide
     * @throws ServerUnavailableException se il server non è raggiungibile
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
                        HttpClientHelper.setCurrentUserId(userInfo.userId());
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
                    if (cause instanceof AuthenticationException) {
                        errorLabel.setText("Invalid credentials");
                    } else if (cause instanceof ServerUnavailableException) {
                        errorLabel.setText("Server unreachable: " + cause.getMessage());
                    } else {
                        errorLabel.setText("Login error: " + cause.getMessage());
                    }
                }); return null; });
    }

    /**
     * Resetta il form di login allo stato iniziale.
     * <p>
     * Pulisce i campi username e password e reimposta l'etichetta
     * di errore al colore e testo predefiniti.
     */
    public void reset() {
        usernameField.clear();
        passwordField.clear();
        errorLabel.setText("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}