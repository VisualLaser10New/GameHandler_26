package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.TableColumns;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.AdminRequestDto;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import com.gameplatform.shared.dto.UpdateTournamentRequestDto;
import com.gameplatform.shared.dto.UsersDirectoryDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dashboard PLATFORM_ADMIN per la gestione della piattaforma.
 * <p>
 * Aggrega sei sezioni, ciascuna supportata dal proprio endpoint locale:
 * <ol>
 *   <li><b>Directory utenti + assegnazione ruoli</b> — {@code GET /api/admin/users}
 *       e {@code POST /api/admin/users/{userId}/roles}.</li>
 *   <li><b>Ciclo di vita tornei</b> — create, open, cancel, schedule,
 *       update, delete con ritorno {@link AdminRequestDto}(PENDING).</li>
 *   <li><b>Classifiche e bracket</b> — riutilizza gli endpoint pubblici
 *       {@code GET /api/tournaments/{id}/standings} e {@code /matches}.</li>
 *   <li><b>Statistiche globali</b> — {@code GET /api/statistics}.</li>
 *   <li><b>Monitor server locale</b> — {@code GET /api/admin/servers/health}.</li>
 *   <li><b>Dashboard in sola lettura</b> — le voci Local/Game Admin sono
 *       accessibili ma le scritture sono bloccate lato server.</li>
 * </ol>
 */
public class PlatformAdminDashboard {

    private final VBox root;
    private final TableView<UsersDirectoryDto> usersTable;
    private final ObservableList<UsersDirectoryDto> usersRows;
    private final TextField rolesField = new TextField(Role.PLAYER.name());
    private final TableView<ServerHealthDto> serversTable;
    private final ObservableList<ServerHealthDto> serversRows;
    private final TextArea createTournamentArea = new TextArea(
            "{\n  \"name\": \"Test Tour\",\n  \"gameType\": \"DARTS\",\n  \"teamBased\": false,\n  \"teamSize\": 1,\n  \"startsAt\": \"2030-01-01T00:00:00Z\",\n  \"buildingIds\": [\"building-1\", \"building-2\"]\n}");
    private final TextArea statsArea = new TextArea();
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private Runnable onNavigateToRequests;

    /**
     * Costruisce la dashboard PLATFORM_ADMIN.
     * <p>
     * Inizializza le tabelle per utenti e server, l'editor del ciclo
     * di vita dei tornei, l'area delle statistiche globali e i
     * pulsanti per le operazioni admin.
     */
    public PlatformAdminDashboard() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("PLATFORM_ADMIN Dashboard");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Button refreshAll = new Button("Refresh all");
        refreshAll.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshAll.setOnAction(e -> refreshAll());

        // ── users directory ──────────────────────────────────────────
        usersRows = FXCollections.observableArrayList();
        usersTable = new TableView<>(usersRows);
        usersTable.setPrefHeight(220);
        TableColumns.addColumn(usersTable, "userId",   UsersDirectoryDto::userId);
        TableColumns.addColumn(usersTable, "username", UsersDirectoryDto::username);
        TableColumns.addColumn(usersTable, "email",    u -> u.email() == null ? "" : u.email());
        TableColumns.addColumn(usersTable, "roles", u -> u.roles() == null ? "" : String.join(",", u.roles()));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rolesField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        Button assignBtn = new Button("Assign roles (selected)");
        assignBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-padding: 6 16;");
        assignBtn.setOnAction(e -> assignRoles());
        HBox usersBar = new HBox(8,
                new Label() {{ setText("New roles (comma-separated):"); setStyle("-fx-text-fill: #ccc;"); setPadding(new Insets(4, 0, 0, 0));}},
                rolesField, assignBtn);
        usersBar.setAlignment(Pos.CENTER_LEFT);

        // ── tournament lifecycle editor ────────────────────────────
        createTournamentArea.setMaxWidth(800);
        createTournamentArea.setPrefRowCount(8);
        createTournamentArea.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4; -fx-font-family: monospace;");

        Button createTBtn = new Button("Create tournament (POST)");
        createTBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        createTBtn.setOnAction(e -> createTournament());
        HBox tourBar = new HBox(8, createTBtn);
        tourBar.setAlignment(Pos.CENTER_LEFT);

        // lifecycle actions onto a selected id
        TextField tourIdField = new TextField();
        tourIdField.setPromptText("tournamentId");
        tourIdField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        Button openBtn     = lifecycleButton(tourIdField, "open");
        Button cancelBtn   = lifecycleButton(tourIdField, "cancel");
        Button scheduleBtn = lifecycleButton(tourIdField, "schedule");

        // update + delete
        TextField updateNameField = new TextField();
        updateNameField.setPromptText("newName");
        updateNameField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        TextField updateStartField = new TextField();
        updateStartField.setPromptText("startsAt ISO-8601 (e.g. 2030-01-01T00:00:00Z)");
        updateStartField.setPrefWidth(220);
        updateStartField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        TextField updateBldField = new TextField();
        updateBldField.setPromptText("buildingIds (comma-separated, ≥2)");
        updateBldField.setPrefWidth(260);
        updateBldField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");

        Button updateBtn = new Button("Update (PUT) DRAFT-only");
        updateBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 6 16;");
        updateBtn.setOnAction(e -> updateTournament(tourIdField.getText(),
                updateNameField.getText(), updateStartField.getText(), updateBldField.getText()));
        Button deleteBtn = new Button("Delete (DELETE) DRAFT-only");
        deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 6 16;");
        deleteBtn.setOnAction(e -> deleteTournament(tourIdField.getText()));

        HBox lifecycleBar = new HBox(8, tourIdField, openBtn, cancelBtn, scheduleBtn);
        lifecycleBar.setAlignment(Pos.CENTER_LEFT);
        HBox updateBar = new HBox(8, updateNameField, updateStartField, updateBldField, updateBtn, deleteBtn);
        updateBar.setAlignment(Pos.CENTER_LEFT);

        // ── global statistics ──────────────────────────────────────
        Button loadStatsBtn = new Button("Load global statistics");
        loadStatsBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 6 16;");
        loadStatsBtn.setOnAction(e -> loadStats());
        statsArea.setPrefRowCount(6);
        statsArea.setMaxWidth(900);
        statsArea.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4; -fx-font-family: monospace;");
        VBox statsBox = new VBox(8, loadStatsBtn, statsArea);

        // ── server monitor ─────────────────────────────────────────
        serversRows = FXCollections.observableArrayList();
        serversTable = new TableView<>(serversRows);
        serversTable.setPrefHeight(180);
        TableColumns.addColumn(serversTable, "buildingId",  s -> s.buildingId());
        TableColumns.addColumn(serversTable, "baseUrl",     s -> s.baseUrl());
        TableColumns.addColumn(serversTable, "lastSeenAt",   s -> s.lastSeenAt() == null ? "—" : s.lastSeenAt().toString());
        TableColumns.addColumn(serversTable, "active",       s -> String.valueOf(s.active()));
        TableColumns.addColumn(serversTable, "pendingReplica", s -> String.valueOf(s.pendingReplicationCount()));

        TableColumn<ServerHealthDto, Void> toggleCol = new TableColumn<>("Action");
        toggleCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Toggle active");
            {
                btn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 4 10;");
                btn.setOnAction(e -> {
                    ServerHealthDto row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;
                    toggleServerActive(row);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
        serversTable.getColumns().add(toggleCol);
        serversTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
        statusLabel.setWrapText(true);

        content.getChildren().addAll(title, refreshAll,
                titled("Users directory (replicated_users)", usersTable, 220), usersBar,
                titled("Tournaments — lifecycle editor (DRAFT only for PUT/DELETE)", createTournamentArea, 160), tourBar,
                lifecycleBar, updateBar,
                titled("Standings & bracket — see \"Tournaments\" (variable)", new Label("(reusing PLAYER views)"), 24),
                titled("Global statistics (GET /api/statistics)", statsBox, 200),
                titled("Local-server monitoring (GET /api/admin/servers/health)", serversTable, 180),
                statusLabel);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        StackPane stack = new StackPane(scroll, loading);
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
     * Aggiorna tutti i dati della dashboard.
     * <p>
     * Carica la directory utenti tramite {@code GET /api/admin/users}
     * e la salute dei server tramite {@code GET /api/admin/servers/health}.
     */
    public void refreshAll() {
        loading.show();
        statusLabel.setText("Refreshing dashboard...");
        var client = ApiClient.instance();
        client.get("/api/admin/users", new TypeReference<List<UsersDirectoryDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> usersRows.setAll(list == null ? List.of() : list)))
                .exceptionally(this::error);
        client.get("/api/admin/servers/health", ServerHealthViewDto.class)
                .thenAccept(view -> Platform.runLater(() -> {
                    if (view == null) {
                        serversRows.clear();
                    } else {
                        serversRows.setAll(view.registeredServers() == null ? List.of() : view.registeredServers());
                        statusLabel.setText("My building: " + view.myBuildingId()
                                + " active=" + view.myServerActive()
                                + " pending=" + view.myPendingOutboxCount());
                    }
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    // ── role assignment ──
    /**
     * Assegna i ruoli all'utente selezionato.
     * <p>
     * Invia una POST asincrona a {@code /api/admin/users/{userId}/roles}
     * con la lista dei ruoli inseriti. In caso di successo reindirizza
     * alla vista delle richieste admin.
     */
    private void assignRoles() {
        UsersDirectoryDto sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a user before assigning roles"); return; }
        String[] parts = rolesField.getText().split(",");
        var body = java.util.Arrays.stream(parts).map(String::strip).filter(s -> !s.isBlank()).toList();
        if (body.isEmpty()) { statusLabel.setText("Empty roles list"); return; }
        loading.show();
        statusLabel.setText("POST /api/admin/users/" + sel.userId() + "/roles ...");
        ApiClient.instance().post("/api/admin/users/" + sel.userId() + "/roles", body, AdminRequestDto.class)
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Assignment PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    /**
     * Alterna lo stato attivo di un server.
     * <p>
     * Invia una PATCH asincrona a {@code /api/admin/servers/{buildingId}/active}
     * con il nuovo stato e aggiorna la dashboard al completamento.
     *
     * @param server il server di cui alternare lo stato; se null non produce effetti
     */
    private void toggleServerActive(ServerHealthDto server) {
        if (server == null) return;
        boolean newActive = !server.active();
        loading.show();
        statusLabel.setText("Toggling " + server.buildingId() + " active=" + newActive + " ...");
        ApiClient.instance().patch("/api/admin/servers/" + server.buildingId() + "/active",
                Map.of("active", newActive), ServerHealthDto.class)
                .thenAccept(s -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Server " + server.buildingId() + " active=" + newActive);
                    refreshAll();
                }))
                .exceptionally(this::error);
    }

    // ── tournament lifecycle ──
    /**
     * Crea un nuovo torneo.
     * <p>
     * Analizza il JSON inserito nell'area di testo come
     * {@link CreateTournamentRequestDto}, valida che buildingIds
     * contenga almeno 2 edifici e invia una POST asincrona a
     * {@code /api/admin/tournaments}. In caso di successo
     * reindirizza alla vista delle richieste admin.
     */
    private void createTournament() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            // Parse JSON typed into the CreateTournamentRequestDto record.
            Map<String, Object> raw = mapper.readValue(createTournamentArea.getText(),
                    new TypeReference<Map<String, Object>>() {});
            String name = (String) raw.get("name");
            GameType gameType = GameType.valueOf(String.valueOf(raw.get("gameType")).toUpperCase());
            boolean teamBased = Boolean.TRUE.equals(raw.get("teamBased"));
            int teamSize = raw.get("teamSize") instanceof Number n ? n.intValue() : 1;
            Instant startsAt = mapper.convertValue(raw.get("startsAt"), Instant.class);
            @SuppressWarnings("unchecked")
            List<String> buildingIds = (List<String>) raw.get("buildingIds");
            if (buildingIds == null || buildingIds.size() < 2) {
                statusLabel.setText("buildingIds must contain at least 2 buildings");
                return;
            }
            CreateTournamentRequestDto body = new CreateTournamentRequestDto(
                    name, gameType, teamBased, teamSize, startsAt, buildingIds);
            loading.show();
            statusLabel.setText("POST /api/admin/tournaments ...");
            ApiClient.instance().post("/api/admin/tournaments", body, AdminRequestDto.class)
                    .thenAccept(req -> Platform.runLater(() -> {
                        loading.hide();
                    statusLabel.setText("Tournament PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
        } catch (Exception e) {
            statusLabel.setText("JSON parse error: " + e.getMessage());
        }
    }

    /**
     * Esegue un'azione sul ciclo di vita di un torneo.
     * <p>
     * Invia una POST asincrona a {@code /api/admin/tournaments/{id}/{action}}
     * per eseguire operazioni come open, cancel o schedule.
     *
     * @param id     l'identificativo del torneo; se null o vuoto non produce effetti
     * @param action l'azione da eseguire (open, cancel, schedule); non null
     */
    private void lifecycle(String id, String action) {
        if (id == null || id.isBlank()) { statusLabel.setText("Enter a tournamentId"); return; }
        loading.show();
        statusLabel.setText("POST /api/admin/tournaments/" + id + "/" + action + " ...");
        ApiClient.instance().postEmpty("/api/admin/tournaments/" + id + "/" + action, AdminRequestDto.class)
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText(action + " PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    /**
     * Aggiorna un torneo esistente (solo DRAFT).
     * <p>
     * Invia una PUT asincrona a {@code /api/admin/tournaments/{id}}
     * con i nuovi parametri (nome, data inizio, edifici).
     *
     * @param id           l'identificativo del torneo; se null o vuoto non produce effetti
     * @param newName      il nuovo nome del torneo
     * @param startsAtStr  la nuova data di inizio in formato ISO-8601
     * @param buildingsCsv la lista di edifici separata da virgole
     */
    private void updateTournament(String id, String newName, String startsAtStr, String buildingsCsv) {
        if (id == null || id.isBlank()) { statusLabel.setText("Enter a tournamentId"); return; }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            UpdateTournamentRequestDto body = new UpdateTournamentRequestDto(
                    newName.isBlank() ? "renamed-tournament" : newName,
                    mapper.readValue("\"" + startsAtStr + "\"", Instant.class),
                    java.util.Arrays.stream(buildingsCsv.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList());
            loading.show();
            statusLabel.setText("PUT /api/admin/tournaments/" + id + " ...");
            ApiClient.instance().put("/api/admin/tournaments/" + id, body, AdminRequestDto.class)
                    .thenAccept(req -> Platform.runLater(() -> {
                        loading.hide();
                        statusLabel.setText("Update PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                        if (onNavigateToRequests != null) onNavigateToRequests.run();
                    }))
                    .exceptionally(this::error);
        } catch (Exception e) {
            statusLabel.setText("Update JSON parse error: " + e.getMessage());
        }
    }

    /**
     * Elimina un torneo (solo DRAFT).
     * <p>
     * Invia una DELETE asincrona a {@code /api/admin/tournaments/{id}}.
     *
     * @param id l'identificativo del torneo; se null o vuoto non produce effetti
     */
    private void deleteTournament(String id) {
        if (id == null || id.isBlank()) { statusLabel.setText("Enter a tournamentId"); return; }
        loading.show();
        statusLabel.setText("DELETE /api/admin/tournaments/" + id + " ...");
        // The DELETE endpoint returns an AdminRequestDto (PENDING) — but our ApiClient
        // has a Void delete() variant; call POST-style via lower-level get for demo.
        ApiClient.instance().delete("/api/admin/tournaments/" + id)
                .thenAccept(v -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Delete accepted (no body) → check Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    // ── global stats ──
    /**
     * Carica le statistiche globali di gioco.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/statistics} e
     * mostra i risultati JSON formattati nell'area di testo dedicata.
     */
    private void loadStats() {
        loading.show();
        ApiClient.instance().get("/api/statistics", new TypeReference<List<JsonNode>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    if (list == null || list.isEmpty()) statsArea.setText("No statistics");
                    else {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode n : list) sb.append(n.toPrettyString()).append("\n");
                        statsArea.setText(sb.toString());
                    }
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    // ── helpers ──
    /**
     * Crea un contenitore con titolo e nodo di contenuto.
     *
     * @param header     il titolo della sezione; non null
     * @param content    il nodo di contenuto; non null
     * @param prefHeight l'altezza preferita (applicata se il contenuto è un {@link Region})
     * @return una {@link VBox} contenente titolo e contenuto
     */
    private static VBox titled(String header, javafx.scene.Node content, int prefHeight) {
        Label h = new Label(header);
        h.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        VBox box = new VBox(4, h, content);
        if (content instanceof Region r) r.setPrefHeight(prefHeight);
        return box;
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
     * Crea un pulsante per un'azione sul ciclo di vita del torneo.
     *
     * @param tourIdField il campo di testo contenente l'ID del torneo; non null
     * @param action      l'azione da eseguire (open, cancel, schedule); non null
     * @return un {@link Button} configurato per l'azione specificata
     */
    private Button lifecycleButton(TextField tourIdField, String action) {
        Button btn = new Button(action.substring(0, 1).toUpperCase() + action.substring(1));
        btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 14;");
        btn.setOnAction(e -> lifecycle(tourIdField.getText(), action));
        return btn;
    }

    private Void error(Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        Platform.runLater(() -> statusLabel.setText("Error: " + msg));
        return null;
    }
}