package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.TableColumns;
import com.gameplatform.shared.domain.model.GameType;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * PLATFORM_ADMIN dashboard (PIANO §7.C line 747-754).
 * <p>
 * The dashboard aggregates six sub-sections, each backed by its
 * dedicated Local endpoint:
 * <ol>
 *   <li><b>Users directory + role assignment</b> — {@code GET /api/admin/users}
 *       returns the replicated user directory (no hashedPassword) as
 *       {@link UsersDirectoryDto} rows. The "Assign roles" button sends a
 *       {@code POST /api/admin/users/{userId}/roles} with a
 *       {@code List<String>} body (raw JSON array of role strings) → the
 *       Local returns an {@link AdminRequestDto}(PENDING) (outbox
 *       {@code ROLE_ASSIGNMENT_REQUESTED}).</li>
 *   <li><b>Tournament lifecycle</b> — create ({@code POST /api/admin/tournaments}),
 *       open/cancel/schedule ({@code POST}/{id}/{action}), update ({@code PUT}),
 *       delete ({@code DELETE} — only DRAFT works server-side). Each returns
 *       the same {@link AdminRequestDto}(PENDING) (outbox
 *       {@code TOURNAMENT_*_REQUESTED}).</li>
 *   <li><b>Standings/bracket read-only</b> — re-uses the public
 *       {@code GET /api/tournaments/{id}/standings} and {@code /matches}
 *       endpoints (rendered through the {@link TournamentsView} submenu
 *       via the navbar — not duplicated here).</li>
 *   <li><b>Global statistics</b> — {@code GET /api/statistics}
 *       (Local aggregated per building).</li>
 *   <li><b>Local server monitor</b> — {@code GET /api/admin/servers/health}
 *       returns the {@link ServerHealthViewDto} aggregating pending outbox
 *       count + the registered local-server registry.</li>
 *   <li><b>Super-set read-only dashboards</b> — the navbar exposes
 *       Local/Game Admin entries to PLATFORM_ADMIN; landing on those
 *       views still shows the data, the buttons ordering writes are
 *       rendered disabled (they are guarded server-side by
 *       {@code @PreAuthorize("hasRole('LOCAL_ADMIN')")}/{@code 'GAME_ADMIN'})
 *       so the writes fail with 403, but the read-from-Local still happens.</li>
 * </ol>
 */
public class PlatformAdminDashboard {

    private final VBox root;
    private final TableView<UsersDirectoryDto> usersTable;
    private final ObservableList<UsersDirectoryDto> usersRows;
    private final TextField rolesField = new TextField("PLAYER");
    private final TableView<ServerHealthDto> serversTable;
    private final ObservableList<ServerHealthDto> serversRows;
    private final TextArea createTournamentArea = new TextArea(
            "{\n  \"name\": \"Test Tour\",\n  \"gameType\": \"DARTS\",\n  \"teamBased\": false,\n  \"teamSize\": 1,\n  \"startsAt\": \"2030-01-01T00:00:00Z\",\n  \"buildingIds\": [\"building-1\", \"building-2\"]\n}");
    private final TextArea statsArea = new TextArea();
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private Runnable onNavigateToRequests;

    public PlatformAdminDashboard() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Dashboard PLATFORM_ADMIN");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Button refreshAll = new Button("Aggiorna tutto");
        refreshAll.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshAll.setOnAction(e -> refreshAll());

        // ── users directory ──────────────────────────────────────────
        usersRows = FXCollections.observableArrayList();
        usersTable = new TableView<>(usersRows);
        usersTable.setPrefHeight(220);
        addPropCol(usersTable, "userId",   "userId");
        addPropCol(usersTable, "username", "username");
        addPropCol(usersTable, "email",    "email");
        TableColumns.addColumn(usersTable, "roles", u -> u.roles() == null ? "" : String.join(",", u.roles()));
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        rolesField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        Button assignBtn = new Button("Assegna ruoli (selezionato)");
        assignBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-padding: 6 16;");
        assignBtn.setOnAction(e -> assignRoles());
        HBox usersBar = new HBox(8,
                new Label() {{ setText("Nuovi ruoli (comma-separated):"); setStyle("-fx-text-fill: #ccc;"); setPadding(new Insets(4, 0, 0, 0));}},
                rolesField, assignBtn);
        usersBar.setAlignment(Pos.CENTER_LEFT);

        // ── tournament lifecycle editor ────────────────────────────
        createTournamentArea.setMaxWidth(800);
        createTournamentArea.setPrefRowCount(8);
        createTournamentArea.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-font-family: monospace;");

        Button createTBtn = new Button("Crea torneo (POST)");
        createTBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        createTBtn.setOnAction(e -> createTournament());
        HBox tourBar = new HBox(8, createTBtn);
        tourBar.setAlignment(Pos.CENTER_LEFT);

        // lifecycle actions onto a selected id
        TextField tourIdField = new TextField();
        tourIdField.setPromptText("tournamentId");
        tourIdField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 6;");
        Button openBtn     = lifecycleButton(tourIdField, "open");
        Button cancelBtn   = lifecycleButton(tourIdField, "cancel");
        Button scheduleBtn = lifecycleButton(tourIdField, "schedule");

        // update + delete
        TextField updateNameField = new TextField();
        updateNameField.setPromptText("newName");
        updateNameField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 6;");
        TextField updateStartField = new TextField();
        updateStartField.setPromptText("startsAt ISO-8601 (e.g. 2030-01-01T00:00:00Z)");
        updateStartField.setPrefWidth(220);
        updateStartField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 6;");
        TextField updateBldField = new TextField();
        updateBldField.setPromptText("buildingIds (comma-separated, ≥2)");
        updateBldField.setPrefWidth(260);
        updateBldField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 6;");

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
        Button loadStatsBtn = new Button("Carica statistiche globali");
        loadStatsBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 6 16;");
        loadStatsBtn.setOnAction(e -> loadStats());
        statsArea.setPrefRowCount(6);
        statsArea.setMaxWidth(900);
        statsArea.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-font-family: monospace;");
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
        serversTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
        statusLabel.setWrapText(true);

        content.getChildren().addAll(title, refreshAll,
                titled("Directory utenti (replicated_users)", usersTable, 220), usersBar,
                titled("Tornei — lifecycle editor (DRAFT only for PUT/DELETE)", createTournamentArea, 160), tourBar,
                lifecycleBar, updateBar,
                titled("Classifiche & bracket — vedi \"Tournaments\" (ruariable)", new Label("(riuso viste PLAYER)"), 24),
                titled("Statistiche globali (GET /api/statistics)", statsBox, 200),
                titled("Monitoraggio local-server (GET /api/admin/servers/health)", serversTable, 180),
                statusLabel);

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
    }

    public Parent getView() {
        return root;
    }

    public void setOnNavigateToRequests(Runnable onNavigateToRequests) {
        this.onNavigateToRequests = onNavigateToRequests;
    }

    public void refreshAll() {
        loading.show();
        statusLabel.setText("Aggiornamento dashboard...");
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
    private void assignRoles() {
        UsersDirectoryDto sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Seleziona un utente prima di assegnare ruoli"); return; }
        String[] parts = rolesField.getText().split(",");
        var body = java.util.Arrays.stream(parts).map(String::strip).filter(s -> !s.isBlank()).toList();
        if (body.isEmpty()) { statusLabel.setText("Elenco ruoli vuoto"); return; }
        loading.show();
        statusLabel.setText("POST /api/admin/users/" + sel.userId() + "/roles ...");
        ApiClient.instance().post("/api/admin/users/" + sel.userId() + "/roles", body, AdminRequestDto.class)
                .thenAccept(req -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Assegnazione PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    // ── tournament lifecycle ──
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
                statusLabel.setText("buildingIds deve contenere almeno 2 edifici");
                return;
            }
            CreateTournamentRequestDto body = new CreateTournamentRequestDto(
                    name, gameType, teamBased, teamSize, startsAt, buildingIds);
            loading.show();
            statusLabel.setText("POST /api/admin/tournaments ...");
            ApiClient.instance().post("/api/admin/tournaments", body, AdminRequestDto.class)
                    .thenAccept(req -> Platform.runLater(() -> {
                        loading.hide();
                        statusLabel.setText("Torneo PENDING (reqId=" + reqId(req) + ") → polling Admin Requests");
                        if (onNavigateToRequests != null) onNavigateToRequests.run();
                    }))
                    .exceptionally(this::error);
        } catch (Exception e) {
            statusLabel.setText("Errore parsing JSON: " + e.getMessage());
        }
    }

    private void lifecycle(String id, String action) {
        if (id == null || id.isBlank()) { statusLabel.setText("Inserisci tournamentId"); return; }
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

    private void updateTournament(String id, String newName, String startsAtStr, String buildingsCsv) {
        if (id == null || id.isBlank()) { statusLabel.setText("Inserisci tournamentId"); return; }
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
            statusLabel.setText("Errore parsing update JSON: " + e.getMessage());
        }
    }

    private void deleteTournament(String id) {
        if (id == null || id.isBlank()) { statusLabel.setText("Inserisci tournamentId"); return; }
        loading.show();
        statusLabel.setText("DELETE /api/admin/tournaments/" + id + " ...");
        // The DELETE endpoint returns an AdminRequestDto (PENDING) — but our ApiClient
        // has a Void delete() variant; call POST-style via lower-level get for demo.
        ApiClient.instance().delete("/api/admin/tournaments/" + id)
                .thenAccept(v -> Platform.runLater(() -> {
                    loading.hide();
                    statusLabel.setText("Delete accepted (no body) → controlla Admin Requests");
                    if (onNavigateToRequests != null) onNavigateToRequests.run();
                }))
                .exceptionally(this::error);
    }

    // ── global stats ──
    private void loadStats() {
        loading.show();
        ApiClient.instance().get("/api/statistics", new TypeReference<List<JsonNode>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    if (list == null || list.isEmpty()) statsArea.setText("Nessuna statistica");
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
    @SuppressWarnings("unchecked")
    private static <S> void addPropCol(TableView<S> table, String header, String property) {
        TableColumn<S, String> col = new TableColumn<>(header);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        table.getColumns().add(col);
    }

    private static VBox titled(String header, javafx.scene.Node content, int prefHeight) {
        Label h = new Label(header);
        h.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        VBox box = new VBox(4, h, content);
        if (content instanceof Region r) r.setPrefHeight(prefHeight);
        return box;
    }

    private static String reqId(AdminRequestDto req) {
        return req == null || req.requestId() == null ? "?" : req.requestId();
    }

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
        Platform.runLater(() -> statusLabel.setText("Errore: " + msg));
        return null;
    }
}