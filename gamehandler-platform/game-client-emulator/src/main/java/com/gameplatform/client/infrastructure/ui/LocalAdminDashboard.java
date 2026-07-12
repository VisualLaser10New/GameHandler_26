package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.TableColumns;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.GameStateDto;
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
    private final Label statLabel = new Label();
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();

    public LocalAdminDashboard() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Dashboard LOCAL_ADMIN");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");
        Label note = new Label("Riepilogo edifici (sola lettura AWS-side)");
        note.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        // ── toolbar ────────────────────────────────────────────────────────
        Button refreshBtn = new Button("Aggiorna tutto");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refreshAll());
        HBox toolbar = new HBox(8, refreshBtn);
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
        Button loadStatsBtn = new Button("Carica statistiche");
        loadStatsBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 6 16;");
        loadStatsBtn.setOnAction(e -> loadStatistics());

        HBox statsBar = new HBox(10,
                new Label() {{ setText("Statistiche edificio — gameType:"); setStyle("-fx-text-fill: #ccc;"); setPadding(new Insets(4, 0, 0, 0)); }},
                gameTypeStatFilter, loadStatsBtn);
        statsBar.setAlignment(Pos.CENTER_LEFT);
        statLabel.setStyle("-fx-text-fill: #eee;");
        statLabel.setWrapText(true);
        statLabel.setMaxWidth(900);
        VBox.setMargin(statLabel, new Insets(0, 0, 0, 20));

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        content.getChildren().addAll(title, note, toolbar,
                titled("Giochi building", gamesTable, 180),
                titled("Sessioni attive", sessionsTable, 180),
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
        loading.show();
        statusLabel.setText("Caricamento dashboard...");
        var client = ApiClient.instance();
        client.get("/api/admin/local/devices", new TypeReference<List<GameStateDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> gamesRows.setAll(list == null ? List.of() : list)))
                .exceptionally(this::error);
        client.get("/api/admin/local/sessions/active", new TypeReference<List<GameSessionDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    sessionsRows.setAll(list == null ? List.of() : list);
                    statusLabel.setText("Aggiornato " + (list == null ? 0 : list.size()) + " sessioni");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    private void loadStatistics() {
        GameType filter = gameTypeStatFilter.getValue();
        if (filter == null) {
            statLabel.setText("Seleziona un gameType");
            return;
        }
        loading.show();
        ApiClient.instance().get("/api/admin/local/statistics",
                "gameType=" + filter.name(),
                new TypeReference<com.fasterxml.jackson.databind.JsonNode>() {})
                .thenAccept(node -> Platform.runLater(() -> {
                    statLabel.setText(node == null ? "Nessun dato" : node.toPrettyString());
                    loading.hide();
                }))
                .exceptionally(this::error);
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
        Platform.runLater(() -> statusLabel.setText("Errore: " + msg));
        return null;
    }
}