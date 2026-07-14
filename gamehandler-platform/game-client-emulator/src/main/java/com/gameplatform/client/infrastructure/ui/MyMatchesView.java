package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.StalenessBadge;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.PlayerMatchDto;
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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Player-scoped match-history view (PIANO §7.C line 737).
 * <p>
 * Polls {@code GET /api/players/me/matches/history[?gameType=]} (the
 * Local {@code PlayerMatchHistoryController}). The COMPLETED filter is
 * applied server-side by the Local server. The view exposes a GameType
 * filter (ComboBox) and a manual Refresh button.
 */
public class MyMatchesView {

    private static final long STALE_THRESHOLD_MS = Long.parseLong(
            System.getProperty("ui.stale-threshold-ms", "300000"));

    private final VBox root;
    private final TableView<PlayerMatchDto> table;
    private final ObservableList<PlayerMatchDto> rows;
    private final ComboBox<GameType> gameTypeFilter;
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private final StalenessBadge staleness;
    private volatile Instant latestUpdatedAt;

    public MyMatchesView() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("My Matches (history)");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Label sub = new Label("GET /api/players/me/matches/history");
        sub.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        gameTypeFilter = new ComboBox<>(FXCollections.observableArrayList(GameType.values()));
        gameTypeFilter.getItems().add(0, null);
        gameTypeFilter.setConverter(new StringConverter<>() {
            @Override public String toString(GameType gt) { return gt == null ? "All games" : gt.name(); }
            @Override public GameType fromString(String s) {
                if (s == null || s.isBlank() || "All games".equals(s)) return null;
                try { return GameType.valueOf(s); } catch (Exception e) { return null; }
            }
        });
        gameTypeFilter.setValue(null);
        gameTypeFilter.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refresh());

        HBox toolbar = new HBox(10,
                new Label() {{ setText("Filter by game:"); setStyle("-fx-text-fill: #ccc;"); setPadding(new Insets(4, 0, 0, 0)); }},
                gameTypeFilter, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        rows = FXCollections.observableArrayList();
        table = new TableView<>(rows);
        TableColumn<PlayerMatchDto, String> gameCol = new TableColumn<>("Game");
        gameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue() == null || c.getValue().gameType() == null ? "" : c.getValue().gameType().name()));
        TableColumn<PlayerMatchDto, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().startedAt() == null ? "" : c.getValue().startedAt().toString()));
        TableColumn<PlayerMatchDto, Integer> durCol = new TableColumn<>("Duration (s)");
        durCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().durationSeconds()));
        TableColumn<PlayerMatchDto, String> winnerCol = new TableColumn<>("Winner");
        winnerCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().winnerId() == null ? "(team)" : c.getValue().winnerId()));
        TableColumn<PlayerMatchDto, String> condCol = new TableColumn<>("WinCondition");
        condCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().winCondition() == null ? "" : c.getValue().winCondition().name()));
        TableColumn<PlayerMatchDto, String> partsCol = new TableColumn<>("Participants");
        partsCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().participants() == null ? ""
                        : String.join(", ", c.getValue().participants())));
        table.getColumns().setAll(List.of(gameCol, startCol, durCol, winnerCol, condCol, partsCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(420);
        table.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #eee;");
        VBox.setVgrow(table, Priority.ALWAYS);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
        staleness = new StalenessBadge(() -> Optional.ofNullable(latestUpdatedAt), STALE_THRESHOLD_MS);

        content.getChildren().addAll(title, sub, toolbar, table, new HBox(statusLabel, staleness));

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");

        gameTypeFilter.setOnAction(e -> refresh());
    }

    public Parent getView() {
        return root;
    }

    public void refresh() {
        loading.show();
        statusLabel.setText("Loading match history...");
        ApiClient client = ApiClient.instance();
        GameType filter = gameTypeFilter.getValue();
        String path = "/api/players/me/matches/history";
        String suffix = filter == null ? "" : "gameType=" + filter.name();
        client.get(path, suffix, new TypeReference<List<PlayerMatchDto>>() {})
                .thenAccept(matches -> Platform.runLater(() -> {
                    rows.setAll(matches == null ? List.of() : matches);
                    latestUpdatedAt = matches == null ? null
                            : matches.stream().map(PlayerMatchDto::endedAt)
                                    .filter(java.util.Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .orElse(Instant.now());
                    staleness.refresh();
                    statusLabel.setText((matches == null ? 0 : matches.size()) + " matches");
                    loading.hide();
                }))
                .exceptionally(ex -> { Platform.runLater(() -> handleError(ex)); return null; });
    }

    private Void handleError(Throwable ex) {
        loading.hide();
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        statusLabel.setText("Error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        return null;
    }
}