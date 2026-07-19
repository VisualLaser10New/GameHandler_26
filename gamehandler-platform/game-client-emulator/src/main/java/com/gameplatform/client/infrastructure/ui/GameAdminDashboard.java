package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.TableColumns;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.GameDefinitionDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.dto.UpsertGameDefinitionRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;

/**
 * Dashboard per l'amministrazione delle definizioni dei giochi (GAME_ADMIN).
 * <p>
 * Mostra il catalogo delle definizioni di gioco esistenti tramite
 * {@code GET /api/games} e fornisce un editor per creare o aggiornare
 * definizioni tramite {@code POST /api/admin/games} e
 * {@code PUT /api/admin/games/{gameType}}. Entrambe le operazioni di
 * scrittura restituiscono un {@link AdminRequestDto} con status PENDING
 * e reindirizzano l'utente alla vista {@link AdminRequestsView} per il polling.
 */
public class GameAdminDashboard {

    private final VBox root;
    private final TableView<GameStateDto> catalogTable;
    private final ObservableList<GameStateDto> catalogRows;
    private final TextField nameField = new TextField();
    private final ComboBox<GameType> gameTypeField = new ComboBox<>(FXCollections.observableArrayList(GameType.values()));
    private final TextField minPlayersField = new TextField("2");
    private final TextField maxPlayersField = new TextField("4");
    private final CheckBox teamAllowedField = new CheckBox("teamAllowed");
    private final TextArea rulesArea = new TextArea("{}");
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private Runnable onNavigateToRequests;

    /**
     * Costruisce la dashboard GAME_ADMIN.
     * <p>
     * Inizializza la tabella del catalogo, l'editor per le definizioni
     * (con campi per gameType, nome, giocatori minimi/massimi, team
     * consentiti e regole di registrazione in JSON) e i pulsanti per
     * refresh, creazione e aggiornamento.
     */
    public GameAdminDashboard() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("GAME_ADMIN Dashboard — game definitions");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");
        Label note = new Label("POST/PUT /api/admin/games → outbox GAME_DEFINITION_UPSERT_REQUESTED → polling");
        note.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        Button refreshBtn = new Button("Refresh catalog");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refreshCatalog());

        Button createBtn = new Button("Submit new definition (POST)");
        createBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        createBtn.setOnAction(e -> submitCreate());

        Button updateBtn = new Button("Update existing definition (PUT)");
        updateBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 6 16;");
        updateBtn.setOnAction(e -> submitUpdate());

        catalogRows = FXCollections.observableArrayList();
        catalogTable = new TableView<>(catalogRows);
        catalogTable.setPrefHeight(220);
        TableColumns.addColumn(catalogTable, "gameId", GameStateDto::gameId);
        TableColumns.addColumn(catalogTable, "name",   GameStateDto::name);
        TableColumns.addColumn(catalogTable, "type",   s -> s.gameType() == null ? "" : s.gameType().name());
        TableColumns.addColumn(catalogTable, "status", s -> s.status() == null ? "" : s.status().name());
        catalogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ── editor grid ─────────────────────────────────────────────────────
        GridPane editor = new GridPane();
        editor.setHgap(10); editor.setVgap(8);
        editor.setPadding(new Insets(10));
        editor.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #444;");
        editor.add(new Label() {{ setText("gameType:"); setStyle("-fx-text-fill: #ccc;");}}, 0, 0);
        gameTypeField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        editor.add(gameTypeField, 1, 0);
        editor.add(new Label() {{ setText("name:"); setStyle("-fx-text-fill: #ccc;");}}, 0, 1);
        nameField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        editor.add(nameField, 1, 1);
        editor.add(new Label() {{ setText("minPlayers:"); setStyle("-fx-text-fill: #ccc;");}}, 0, 2);
        minPlayersField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        editor.add(minPlayersField, 1, 2);
        editor.add(new Label() {{ setText("maxPlayers:"); setStyle("-fx-text-fill: #ccc;");}}, 0, 3);
        maxPlayersField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        editor.add(maxPlayersField, 1, 3);
        editor.add(new Label() {{ setText("Team allowed:"); setStyle("-fx-text-fill: #ccc;");}}, 0, 4);
        teamAllowedField.setStyle("-fx-text-fill: #eee;");
        editor.add(teamAllowedField, 1, 4);
        editor.add(new Label() {{ setText("registrationRules (JSON):"); setStyle("-fx-text-fill: #ccc;");}}, 0, 5);
        rulesArea.setPrefRowCount(3);
        rulesArea.setMaxWidth(420);
        rulesArea.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4; -fx-font-family: monospace;");
        editor.add(rulesArea, 1, 5);
        HBox actions = new HBox(8, createBtn, updateBtn);
        editor.add(actions, 1, 6);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
        statusLabel.setWrapText(true);

        content.getChildren().addAll(title, note, refreshBtn, catalogTable,
                new Label() {{ setText("Editor"); setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");}},
                editor, statusLabel);

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
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
     * Registra il callback per la navigazione verso la vista delle richieste admin.
     *
     * @param onNavigateToRequests l'azione da eseguire per navigare verso
     *                             {@link AdminRequestsView}; può essere null
     */
    public void setOnNavigateToRequests(Runnable onNavigateToRequests) {
        this.onNavigateToRequests = onNavigateToRequests;
    }

    /**
     * Carica il catalogo delle definizioni di gioco dal server locale.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/games} e aggiorna
     * la tabella del catalogo con i risultati. In caso di risposta nulla
     * imposta una lista vuota.
     */
    public void refreshCatalog() {
        loading.show();
        statusLabel.setText("Loading catalog...");
        ApiClient.instance().get("/api/games", new TypeReference<List<GameStateDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    catalogRows.setAll(list == null ? List.of() : list);
                    statusLabel.setText((list == null ? 0 : list.size()) + " definitions");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    /**
     * Invia una richiesta di creazione di una nuova definizione di gioco.
     * <p>
     * Valida che il gameType sia selezionato, costruisce il corpo della
     * richiesta e invia una POST asincrona a {@code /api/admin/games}.
     * In caso di successo reindirizza alla vista delle richieste admin
     * tramite il callback {@link #onNavigateToRequests}.
     */
    private void submitCreate() {
        GameType gt = gameTypeField.getValue();
        if (gt == null) { statusLabel.setText("Select a gameType"); return; }
        Map<String, Object> rules = parseRulesOrEmpty();
        var body = new UpsertGameDefinitionRequestDto(gt, nameField.getText().trim(),
                parseIntOr(minPlayersField, 1), parseIntOr(maxPlayersField, 1),
                teamAllowedField.isSelected(), rules);
        loading.show();
        statusLabel.setText("Sending definition (POST /api/admin/games)...");
        ApiClient.instance().post("/api/admin/games", body, AdminRequestDto.class)
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Definition PENDING (reqId=" + reqId(req) + ") → polling in Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    /**
     * Invia una richiesta di aggiornamento di una definizione di gioco esistente.
     * <p>
     * Valida che il gameType sia selezionato, costruisce il corpo della
     * richiesta e invia una PUT asincrona a {@code /api/admin/games/{gameType}}.
     * In caso di successo reindirizza alla vista delle richieste admin
     * tramite il callback {@link #onNavigateToRequests}.
     */
    private void submitUpdate() {
        GameType gt = gameTypeField.getValue();
        if (gt == null) { statusLabel.setText("Select a gameType"); return; }
        Map<String, Object> rules = parseRulesOrEmpty();
        var body = new UpsertGameDefinitionRequestDto(gt, nameField.getText().trim(),
                parseIntOr(minPlayersField, 1), parseIntOr(maxPlayersField, 1),
                teamAllowedField.isSelected(), rules);
        loading.show();
        statusLabel.setText("Sending update (PUT /api/admin/games/" + gt + ")...");
        ApiClient.instance().put("/api/admin/games/" + gt.name(), body, AdminRequestDto.class)
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Update PENDING (reqId=" + reqId(req) + ") → polling in Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    /**
     * Restituisce l'identificativo di una richiesta admin in forma sicura.
     *
     * @param req la richiesta admin; può essere null
     * @return l'identificativo della richiesta, o "?" se null o senza ID
     */
    private static String reqId(AdminRequestDto req) {
        return req == null || req.requestId() == null ? "?" : req.requestId();
    }

    /**
     * Converte il testo di un campo in un intero, con valore predefinito.
     *
     * @param tf       il campo di testo da parsare; non null
     * @param fallback il valore predefinito se la conversione fallisce
     * @return il valore intero parsato, o {@code fallback} in caso di errore
     */
    private static int parseIntOr(TextField tf, int fallback) {
        try { return Integer.parseInt(tf.getText().trim()); } catch (Exception e) { return fallback; }
    }

    /**
     * Analizza il contenuto dell'area di testo delle regole come JSON.
     * <p>
     * Se il contenuto è nullo, vuoto o non è un JSON valido, restituisce
     * una mappa vuota e aggiorna l'etichetta di stato con un messaggio
     * di avviso.
     *
     * @return una {@link Map} contenente le regole parsate, o una mappa
     *         vuota in caso di errore
     */
    private Map<String, Object> parseRulesOrEmpty() {
        String s = rulesArea.getText();
        if (s == null || s.isBlank()) return Map.of();
        try {
            return new ObjectMapper().readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            statusLabel.setText("registrationRules is not valid JSON → using {} (steps: " + e.getMessage() + ")");
            return Map.of();
        }
    }

    /**
     * Gestisce un errore asincrono delle chiamate API.
     * <p>
     * Nasconde l'indicatore di caricamento, risale la catena delle
     * eccezioni fino alla causa radice e aggiorna l'etichetta di
     * stato con il messaggio di errore.
     *
     * @param ex l'eccezione da gestire; può essere null
     * @return sempre null
     */
    private Void error(Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        Platform.runLater(() -> statusLabel.setText("Error: " + msg));
        return null;
    }
}