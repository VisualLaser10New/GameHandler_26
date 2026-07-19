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
 * Dashboard LOCAL_ADMIN per la gestione dell'edificio.
 * <p>
 * Aggrega gli endpoint admin con scope edificio esposti da
 * {@code AdminLocalController}:
 * <ul>
 *   <li>{@code GET /api/admin/local/devices} → lista giochi (tabella {@link GameStateDto});</li>
 *   <li>{@code GET /api/admin/local/sessions/active} → sessioni attive (tabella {@link GameSessionDto});</li>
 *   <li>{@code GET /api/admin/local/statistics?gameType=XXX} → statistiche
 *       locali per edificio (renderizzate come scheda dopo la selezione del gameType).</li>
 * </ul>
 * La vista è in sola lettura: le operazioni di scrittura CRUD sono
 * delegate a viste successive.
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

    /**
     * Costruisce la dashboard LOCAL_ADMIN.
     * <p>
     * Inizializza le tabelle per dispositivi e sessioni attive, i filtri
     * per le statistiche, il selettore di edificio (per PLATFORM_ADMIN)
     * e i pulsanti per refresh, aggiunta gioco e caricamento statistiche.
     */
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
        gameTypeStatFilter.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
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

    /**
     * Restituisce il nodo radice JavaFX per questa vista.
     *
     * @return il nodo {@link Parent} radice
     */
    public Parent getView() {
        return root;
    }

    /**
     * Aggiorna tutti i dati della dashboard.
     * <p>
     * Carica l'elenco dei dispositivi e delle sessioni attive dal server
     * locale. Installa il selettore di edificio se l'utente è
     * PLATFORM_ADMIN.
     */
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

    /**
     * Carica le statistiche per il tipo di gioco selezionato.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/admin/local/statistics}
     * con il parametro {@code gameType} selezionato. Mostra il risultato
     * JSON formattato nell'area di testo dedicata.
     */
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

    /**
     * Mostra un dialogo per l'aggiunta di un nuovo gioco all'edificio.
     * <p>
     * Presenta un form con selezione del tipo di gioco e nome.
     * Invia una POST asincrona a {@code /api/admin/local/games}
     * e aggiorna la dashboard al completamento.
     */
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
        typeCombo.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        TextField nameField = new TextField("New game");
        nameField.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
        VBox box = new VBox(8, new Label("Game type:"), typeCombo, new Label("Name:"), nameField);
        box.setPadding(new Insets(10));

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Add game");
        alert.setHeaderText("Create a new game instance in this building");
        java.net.URL darkCss = LocalAdminDashboard.class.getResource("/styles/dark-theme.css");
        if (darkCss != null) {
            alert.getDialogPane().getStylesheets().add(darkCss.toExternalForm());
        }
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
        statusLabel.setText("Creating game " + gameType + " '" + name + "'...");
        loading.show();
        Map<String, Object> body = Map.of("gameType", gameType, "name", name);
        ApiClient.instance().post("/api/admin/local/games", body, GameStateDto.class)
                .thenAccept(g -> Platform.runLater(() -> {
                    loading.hide();
                    Alert done = new Alert(Alert.AlertType.INFORMATION,
                            "Game created successfully" + (g == null ? "" : ": " + g.name() + " (" + g.gameId() + ")"));
                    done.setHeaderText("Add game");
                    done.show();
                    refreshAll();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        loading.hide();
                        Throwable t = ex;
                        while (t != null && t.getCause() != null) t = t.getCause();
                        String msg = t == null ? "(unknown)" : (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                        Alert err = new Alert(Alert.AlertType.ERROR, "Failed to create game: " + msg);
                        err.setHeaderText("Add game");
                        err.show();
                        refreshAll();
                    });
                    return null;
                });
    }

    /**
     * Rimuove un gioco dall'edificio.
     * <p>
     * Invia una DELETE asincrona a {@code /api/admin/local/games/{gameId}}
     * e aggiorna la dashboard al completamento.
     *
     * @param game il gioco da rimuovere; se null non produce effetti
     */
    private void removeGame(GameStateDto game) {
        if (game == null) return;
        loading.show();
        ApiClient.instance().delete("/api/admin/local/games/" + game.gameId())
                .thenAccept(v -> Platform.runLater(() -> {
                    loading.hide();
                    Alert done = new Alert(Alert.AlertType.INFORMATION, "Game removed: " + game.name());
                    done.setHeaderText("Remove game");
                    done.show();
                    refreshAll();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        loading.hide();
                        Throwable t = ex;
                        while (t != null && t.getCause() != null) t = t.getCause();
                        String msg = t == null ? "(unknown)" : (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                        Alert err = new Alert(Alert.AlertType.ERROR, "Failed to remove game: " + msg);
                        err.setHeaderText("Remove game");
                        err.show();
                        refreshAll();
                    });
                    return null;
                });
    }

    /**
     * Alterna lo stato di un gioco tra AVAILABLE e MAINTENANCE.
     * <p>
     * Invia una PUT asincrona a {@code /api/admin/local/games/{gameId}}
     * con il nuovo stato e aggiorna la dashboard al completamento.
     *
     * @param game il gioco di cui alternare lo stato; se null non produce effetti
     */
    private void toggleGameStatus(GameStateDto game) {
        if (game == null) return;
        String current = game.status() == null ? "" : game.status().name();
        String newStatus = "AVAILABLE".equals(current) ? "MAINTENANCE" : "AVAILABLE";
        loading.show();
        Map<String, Object> body = Map.of("status", newStatus);
        ApiClient.instance().put("/api/admin/local/games/" + game.gameId(), body, GameStateDto.class)
                .thenAccept(g -> Platform.runLater(() -> {
                    loading.hide();
                    Alert done = new Alert(Alert.AlertType.INFORMATION,
                            "Game '" + game.name() + "' set to " + newStatus);
                    done.setHeaderText("Toggle game status");
                    done.show();
                    refreshAll();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        loading.hide();
                        Throwable t = ex;
                        while (t != null && t.getCause() != null) t = t.getCause();
                        String msg = t == null ? "(unknown)" : (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                        Alert err = new Alert(Alert.AlertType.ERROR, "Failed to toggle status: " + msg);
                        err.setHeaderText("Toggle game status");
                        err.show();
                        refreshAll();
                    });
                    return null;
                });
    }

    /**
     * Cambia l'edificio attivo per la dashboard.
     * <p>
     * Aggiorna l'URL base del client API e ricarica tutti i dati.
     *
     * @param buildingId l'identificativo dell'edificio; se null o vuoto
     *                   non produce effetti
     */
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
     * Installa il selettore di edificio per PLATFORM_ADMIN.
     * <p>
     * La verifica del ruolo è differita rispetto al costruttore perché
     * i ruoli vengono popolati dopo il login. Metodo idempotente: una
     * volta installato, le chiamate successive non producono effetti.
     * La lista degli edifici selezionabili viene recuperata dinamicamente
     * da {@code GET /api/admin/servers/health} filtrando per server
     * attivi. Se l'API fallisce, utilizza "building-1" come predefinito.
     */
    private void ensureBuildingSelectorIfPlatformAdmin() {
        if (buildingSelectorInstalled) return;
        if (!HttpClientHelper.hasRole(Role.PLATFORM_ADMIN.name())) return;
        buildingSelector.setStyle("-fx-background-color: #333; -fx-text-fill: #eee; -fx-prompt-text-fill: #888; -fx-padding: 8; -fx-background-radius: 4;");
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

    /**
     * Crea un contenitore con titolo e tabella.
     *
     * @param header     il titolo della sezione; non null
     * @param tv         la tabella da includere; non null
     * @param prefHeight l'altezza preferita della tabella
     * @return una {@link VBox} contenente titolo e tabella
     */
    private static VBox titled(String header, TableView<?> tv, int prefHeight) {
        Label h = new Label(header);
        h.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        VBox box = new VBox(4, h, tv);
        tv.setPrefHeight(prefHeight);
        return box;
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