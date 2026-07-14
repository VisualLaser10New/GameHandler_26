package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.TableColumns;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.GameStateDto;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Map;

/**
 * LOCAL_ADMIN dashboard (PIANO §7.C line 743-744).
 * <p>
 * Aggregates the building-scoped admin endpoints exposed by
 * {@code AdminLocalController}:
 * <ul>
 *   <li>{@code GET /api/admin/local/devices} → games list (table {@link GameStateDto});</li>
 *   <li>{@code GET /api/admin/local/sessions/active} → active sessions (table {@link GameSessionDto});</li>
 *   <li>{@code GET /api/admin/local/statistics?gameType=XXX} → building-scoped
 *       local statistics (rendered as a card after the {@code gameType} is
 *       selected).</li>
 * </ul>
 * No CRUD buttons: the {@code AdminLocalController} CRUD-on-games endpoints
 * are documented in the dashboard comment but editing is intentionally
 * read-only here (creates/updates/deletes are out of scope of the FASE 7
 * UI — see PIANO §7.C line 743 "endpoint esistenti su AdminLocalController").
 * The fine-grained writes are deferred to a follow-up; the view only
 * consumes the three read endpoints (which match the spec wording
 * "giochi building … dispositivi … sessioni attive … statistiche edificio").
 */
public class LocalAdminDashboard {

    private final VBox root;
    private final TableView<GameStateDto> gamesTable;
    private final ObservableList<GameStateDto> gamesRows;
    private final TableView<GameSessionDto> sessionsTable;
    private final ObservableList<GameSessionDto> sessionsRows;
    private final ComboBox<GameType> gameTypeStatFilter;
    private final ComboBox<String> buildingSelector = new ComboBox<>();
    private final Label statLabel = new Label();
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private boolean buildingSelectorInstalled = false;
    private final HBox toolbar = new HBox(8);

    public LocalAdminDashboard() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Dashboard LOCAL_ADMIN");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");
        Label note = new Label("Building summary (read-only)");
        note.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        // ── toolbar ────────────────────────────────────────────────────────
        Button refreshBtn = new Button("Refresh all");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refreshAll());

        Button addGameBtn = new Button("Add game");
        addGameBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 6 16;");
        addGameBtn.setOnAction(e -> showAddGameDialog());

        toolbar.getChildren().addAll(refreshBtn, addGameBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // ── games table ─────────────────────────────────────────────────────
        gamesRows = FXCollections.observableArrayList();
        gamesTable = new TableView<>(gamesRows);
        gamesTable.setPrefHeight(180);
        TableColumns.addColumn(gamesTable, "gameId", GameStateDto::gameId);
        TableColumns.addColumn(gamesTable, "name",   GameStateDto::name);
        TableColumns.addColumn(gamesTable, "game",   s -> s.gameType() == null ? "" : s.gameType().name());
        TableColumns.addColumn(gamesTable, "status", s -> s.status() == null ? "" : s.status().name());
        TableColumns.addColumn(gamesTable, "min/max", s -> s.minPlayers() + "/" + s.maxPlayers());

        TableColumn<GameStateDto, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            private final Button toggleBtn = new Button("Toggle");
            {
                removeBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-padding: 4 10;");
                toggleBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 4 10;");
                removeBtn.setOnAction(e -> removeGame(getTableRow() == null ? null : getTableRow().getItem()));
                toggleBtn.setOnAction(e -> toggleGameStatus(getTableRow() == null ? null : getTableRow().getItem()));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(new HBox(4, toggleBtn, removeBtn));
                }
            }
        });
        gamesTable.getColumns().add(actionsCol);
        gamesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ── active sessions table ───────────────────────────────────────────
        sessionsRows = FXCollections.observableArrayList();
        sessionsTable = new TableView<>(sessionsRows);
        sessionsTable.setPrefHeight(180);
        TableColumns.addColumn(sessionsTable, "id",      GameSessionDto::id);
        TableColumns.addColumn(sessionsTable, "gameId",  GameSessionDto::gameId);
        TableColumns.addColumn(sessionsTable, "type",    s -> s.gameType() == null ? "" : s.gameType().name());
        TableColumns.addColumn(sessionsTable, "status",  s -> s.status() == null ? "" : s.status().name());
        TableColumns.addColumn(sessionsTable, "start",   s -> s.startedAt() == null ? "" : s.startedAt().toString());
        TableColumns.addColumn(sessionsTable, "players", s -> s.participants() == null ? "" : String.join(",", s.participants()));
        sessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ── statistics row ──────────────────────────────────────────────────
        gameTypeStatFilter = new ComboBox<>(FXCollections.observableArrayList(GameType.values()));
        gameTypeStatFilter.setConverter(new StringConverter<>() {
            @Override public String toString(GameType gt) { return gt == null ? "(select)" : gt.name(); }
            @Override public GameType fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return GameType.valueOf(s); } catch (Exception e) { return null; }
            }
        });
        gameTypeStatFilter.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        Button loadStatsBtn = new Button("Load statistics");
        loadStatsBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 6 16;");
        loadStatsBtn.setOnAction(e -> loadStatistics());

        HBox statsBar = new HBox(10,
                new Label() {{ setText("Building statistics — gameType:"); setStyle("-fx-text-fill: #ccc;"); setPadding(new Insets(4, 0, 0, 0)); }},
                gameTypeStatFilter, loadStatsBtn);
        statsBar.setAlignment(Pos.CENTER_LEFT);
        statLabel.setStyle("-fx-text-fill: #eee;");
        statLabel.setWrapText(true);
        statLabel.setMaxWidth(900);
        VBox.setMargin(statLabel, new Insets(0, 0, 0, 20));

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        content.getChildren().addAll(title, note, toolbar,
                titled("Building games", gamesTable, 180),
                titled("Active sessions", sessionsTable, 180),
                statsBar, statLabel, statusLabel);

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
    }

    public Parent getView() {
        return root;
    }

    public void refreshAll() {
        ensureBuildingSelectorIfPlatformAdmin();
        loading.show();
        statusLabel.setText("Loading dashboard...");
        gamesRows.clear();
        sessionsRows.clear();
        var client = ApiClient.instance();
        client.get("/api/admin/local/devices", new TypeReference<List<GameStateDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> gamesRows.setAll(list == null ? List.of() : list)))
                .exceptionally(this::error);
        client.get("/api/admin/local/sessions/active", new TypeReference<List<GameSessionDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    sessionsRows.setAll(list == null ? List.of() : list);
                    statusLabel.setText("Updated " + (list == null ? 0 : list.size()) + " sessions");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    private void loadStatistics() {
        GameType filter = gameTypeStatFilter.getValue();
        if (filter == null) {
            statLabel.setText("Select a gameType");
            return;
        }
        loading.show();
        ApiClient.instance().get("/api/admin/local/statistics",
                "gameType=" + filter.name(),
                new TypeReference<com.fasterxml.jackson.databind.JsonNode>() {})
                .thenAccept(node -> Platform.runLater(() -> {
                    statLabel.setText(node == null ? "No data" : node.toPrettyString());
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    private void showAddGameDialog() {
        ComboBox<GameType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(GameType.values()));
        typeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(GameType gt) { return gt == null ? "" : gt.name(); }
            @Override public GameType fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return GameType.valueOf(s); } catch (Exception e) { return null; }
            }
        });
        typeCombo.setValue(GameType.CHESS);
        typeCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        TextField nameField = new TextField("New game");
        nameField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        VBox box = new VBox(8, new Label("Game type:"), typeCombo, new Label("Name:"), nameField);
        box.setPadding(new Insets(10));

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Add game");
        alert.setHeaderText("Create a new game instance in this building");
        alert.getDialogPane().setContent(box);
        java.util.Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            System.err.println("DEBUG ADD GAME: dialog dismissed without OK");
            return;
        }
        String gameType = typeCombo.getValue() == null ? "" : typeCombo.getValue().name();
        String name = nameField.getText() == null ? "" : nameField.getText();
        System.err.println("DEBUG ADD GAME: gameType=" + gameType + " name=" + name);
        if (gameType.isBlank() || name.isBlank()) {
            statusLabel.setText("gameType and name are required");
            return;
        }
        loading.show();
        Map<String, Object> body = Map.of("gameType", gameType, "name", name);
        ApiClient.instance().post("/api/admin/local/games", body, GameStateDto.class)
                .thenAccept(g -> Platform.runLater(() -> { statusLabel.setText("Game created"); refreshAll(); }))
                .exceptionally(ex -> {
                    System.err.println("DEBUG ADD GAME: POST failed: " + ex);
                    ex.printStackTrace();
                    return error(ex);
                });
    }

    private void removeGame(GameStateDto game) {
        if (game == null) return;
        loading.show();
        ApiClient.instance().delete("/api/admin/local/games/" + game.gameId())
                .thenAccept(v -> Platform.runLater(() -> { statusLabel.setText("Game removed"); refreshAll(); }))
                .exceptionally(this::error);
    }

    private void toggleGameStatus(GameStateDto game) {
        if (game == null) return;
        String current = game.status() == null ? "" : game.status().name();
        String newStatus = "AVAILABLE".equals(current) ? "MAINTENANCE" : "AVAILABLE";
        loading.show();
        Map<String, Object> body = Map.of("status", newStatus);
        ApiClient.instance().put("/api/admin/local/games/" + game.gameId(), body, GameStateDto.class)
                .thenAccept(g -> Platform.runLater(() -> { statusLabel.setText("Game set to " + newStatus); refreshAll(); }))
                .exceptionally(this::error);
    }

    private void switchBuilding(String buildingId) {
        if (buildingId == null || buildingId.isBlank()) return;
        String url = ApiClient.BUILDING_URLS.get(buildingId);
        if (url == null) {
            statusLabel.setText("Unknown building: " + buildingId);
            return;
        }
        ApiClient.instance().setBaseUrl(url);
        statusLabel.setText("Switched to " + buildingId + " (" + url + ")");
        refreshAll();
    }

    /**
     * Installs the {@code buildingSelector} into the toolbar the first time
     * this view is shown to a PLATFORM_ADMIN. The check is deferred from
     * the constructor (where it would always return false because the
     * roles are populated after login, see {@link LoginView#performLogin}
     * {@code GET /api/auth/me} → {@link HttpClientHelper#setRoles}).
     * Idempotent: once installed, subsequent calls are no-ops.
     * <p>
     * The list of selectable buildings is recovered dynamically from
     * {@code GET /api/admin/servers/health} ({@link ServerHealthViewDto#registeredServers()})
     * filtering by {@code active == true} so the combobox only offers the
     * nodes that are currently registered AND active on this Local Server.
     * If the API fails or returns no active server, falls back to
     * {@code "building-1"} so the combobox is never left empty.
     */
    private void ensureBuildingSelectorIfPlatformAdmin() {
        if (buildingSelectorInstalled) return;
        if (!HttpClientHelper.hasRole(Role.PLATFORM_ADMIN.name())) return;
        buildingSelector.setStyle("-fx-background-color: #333; -fx-text-fill: #eee;");
        buildingSelector.setOnAction(e -> switchBuilding(buildingSelector.getValue()));
        Label bLabel = new Label("Building:");
        bLabel.setStyle("-fx-text-fill: #ccc;");
        HBox buildingBar = new HBox(8, bLabel, buildingSelector);
        buildingBar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().add(buildingBar);
        buildingSelectorInstalled = true;

        ApiClient.instance().get("/api/admin/servers/health", ServerHealthViewDto.class)
                .thenAccept(view -> Platform.runLater(() -> {
                    buildingSelector.getItems().clear();
                    boolean any = false;
                    if (view != null && view.registeredServers() != null) {
                        for (ServerHealthDto s : view.registeredServers()) {
                            if (s.active()) {
                                buildingSelector.getItems().add(s.buildingId());
                                any = true;
                            }
                        }
                    }
                    if (!any) {
                        buildingSelector.getItems().add("building-1");
                    }
                    buildingSelector.setValue(buildingSelector.getItems().get(0));
                    statusLabel.setText("Loaded " + buildingSelector.getItems().size() + " active building(s)");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        buildingSelector.getItems().setAll("building-1");
                        buildingSelector.setValue("building-1");
                        Throwable t = ex;
                        while (t != null && t.getCause() != null) t = t.getCause();
                        statusLabel.setText("Building list unreachable — defaulting to building-1"
                                + (t == null ? "" : " (" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()) + ")"));
                    });
                    return null;
                });
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static VBox titled(String header, TableView<?> tv, int prefHeight) {
        Label h = new Label(header);
        h.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        VBox box = new VBox(4, h, tv);
        tv.setPrefHeight(prefHeight);
        return box;
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