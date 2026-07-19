package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.domain.exception.AuthenticationException;
import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.domain.exception.ServerUnavailableException;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.gameplatform.client.infrastructure.ui.components.StalenessBadge;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
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
 * Vista delle statistiche personali del giocatore.
 * <p>
 * Recupera i dati tramite {@code GET /api/players/me/statistics[?gameType=]}
 * dal server locale e renderizza ogni {@link PlayerStatisticsDto} come
 * riga di una {@link TableView}. Espone un filtro {@link ComboBox} sul
 * tipo di gioco. Il refresh è manuale tramite pulsante Refresh.
 */
public class MyStatisticsView {

    private static final long STALE_THRESHOLD_MS = Long.parseLong(
            System.getProperty("ui.stale-threshold-ms", "300000"));

    private final VBox root;
    private final TableView<PlayerStatisticsDto> table;
    private final ObservableList<PlayerStatisticsDto> rows;
    private final ComboBox<GameType> gameTypeFilter;
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private final StalenessBadge staleness;
    private Runnable onNavigateToRequests;

    private volatile Instant latestUpdatedAt;

    /**
     * Costruisce la vista delle statistiche personali.
     * <p>
     * Inizializza la tabella, il filtro per tipo di gioco, il
     * pulsante di refresh e l'indicatore di obsolescenza dei dati.
     */
    public MyStatisticsView() {
        VBox content = new VBox(8);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");
        content.setSpacing(10);

        Label title = new Label("My Statistics");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        Label sub = new Label("GET /api/players/me/statistics");
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
        TableColumn<PlayerStatisticsDto, String> gameCol = new TableColumn<>("Game");
        gameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue() == null || c.getValue().gameType() == null ? "" : c.getValue().gameType().name()));
        TableColumn<PlayerStatisticsDto, Integer> playedCol = new TableColumn<>("Matches played");
        playedCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().matchesPlayed()));
        TableColumn<PlayerStatisticsDto, Integer> wonCol = new TableColumn<>("Wins");
        wonCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().matchesWon()));
        TableColumn<PlayerStatisticsDto, String> lastCol = new TableColumn<>("Last match");
        lastCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().lastPlayedAt() == null ? "—" : c.getValue().lastPlayedAt().toString()));
        table.getColumns().setAll(List.of(gameCol, playedCol, wonCol, lastCol));
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

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Aggiorna le statistiche personali dal server.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/players/me/statistics}
     * con il filtro gameType opzionale. Aggiorna la tabella con i risultati
     * e l'indicatore di obsolescenza.
     */
    public void refresh() {
        loading.show();
        statusLabel.setText("Loading statistics...");
        ApiClient client = ApiClient.instance();
        GameType filter = gameTypeFilter.getValue();
        String path = "/api/players/me/statistics";
        String suffix = filter == null ? "" : "gameType=" + filter.name();
        client.get(path, suffix, new TypeReference<List<PlayerStatisticsDto>>() {})
                .thenAccept(stats -> Platform.runLater(() -> {
                    rows.setAll(stats == null ? List.of() : stats);
                    latestUpdatedAt = stats == null ? null
                            : stats.stream().map(PlayerStatisticsDto::lastPlayedAt)
                                    .filter(java.util.Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .orElse(Instant.now());
                    staleness.refresh();
                    statusLabel.setText((stats == null ? 0 : stats.size()) + " entries");
                    loading.hide();
                }))
                .exceptionally(ex -> { Platform.runLater(() -> handleError(ex)); return null; });
    }

    /**
     * Gestisce un errore asincrono delle chiamate API.
     * <p>
     * Nasconde l'indicatore di caricamento e mostra un messaggio
     * specifico in base al tipo di eccezione (ServerUnavailableException,
     * AuthenticationException o errore generico).
     *
     * @param ex l'eccezione da gestire; può essere null
     * @return sempre null
     */
    private Void handleError(Throwable ex) {
        loading.hide();
        String cause = rootCause(ex);
        if (ex.getCause() instanceof ServerUnavailableException
                || ex.getCause() instanceof AuthenticationException) {
            statusLabel.setText("Error: " + ex.getCause().getMessage());
        } else {
            statusLabel.setText("Error loading statistics: " + cause);
        }
        return null;
    }

    /**
     * Risale la catena delle eccezioni fino alla causa radice.
     *
     * @param ex l'eccezione iniziale; può essere null
     * @return il messaggio della causa radice, o il nome della classe se null
     */
    private static String rootCause(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}