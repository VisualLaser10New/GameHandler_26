package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.client.infrastructure.ui.components.LoadingIndicator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.gameplatform.shared.dto.AdminRequestDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Admin-requests polling view (PIANO §7.C line 755).
 * <p>
 * Lists the authenticated user's own requests ({@code GET /api/admin/requests}
 * — the Local server filters by {@code actingUserId == principal}) and
 * refreshes every {@code 8 s}. Each request is rendered as a card whose
 * visual state derives from its {@code status} field:
 * <ul>
 *   <li>{@code PENDING} → JavaFX {@link ProgressIndicator} + "in attesa di conferma…";</li>
 *   <li>{@code COMPLETED} → green ✓ + the {@code resultData} text (parsed for a {@code reason} field);</li>
 *   <li>{@code FAILED} → red banner "Operazione non confermata entro il timeout — riprova/riesamina"
 *       + the readable {@code result_data.reason}.</li>
 * </ul>
 */
public class AdminRequestsView {

    private static final long POLL_INTERVAL_MS = 8_000L;

    private final VBox root;
    private final VBox cardsContainer;
    private final Label statusLabel = new Label();
    private final LoadingIndicator loading = new LoadingIndicator();
    private final ObservableList<AdminRequestDto> rows = FXCollections.observableArrayList();

    private javafx.animation.Timeline poller;
    private volatile Instant latestUpdatedAt;

    public AdminRequestsView() {
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        Label title = new Label("Stato richieste admin");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");
        Label sub = new Label("Polling GET /api/admin/requests ogni " + (POLL_INTERVAL_MS / 1000) + "s");
        sub.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        Button refreshBtn = new Button("Aggiorna ora");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6 16;");
        refreshBtn.setOnAction(e -> refresh());

        HBox toolbar = new HBox(8, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        cardsContainer = new VBox(6);

        ScrollPane scroll = new ScrollPane(cardsContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        content.getChildren().addAll(title, sub, toolbar, scroll, statusLabel);

        StackPane stack = new StackPane(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        root = new VBox(stack);
        root.setStyle("-fx-padding: 0; -fx-background-color: #1e1e1e;");
    }

    public Parent getView() {
        return root;
    }

    /** Called by MainView when the user navigates to this view — starts polling. */
    public void onEnter() {
        refresh();
        if (poller == null) {
            poller = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(POLL_INTERVAL_MS), e -> refresh()));
            poller.setCycleCount(javafx.animation.Animation.INDEFINITE);
        }
        poller.play();
    }

    /** Called by MainView when the user navigates away — stops the poller. */
    public void onLeave() {
        if (poller != null) poller.stop();
    }

    private void refresh() {
        loading.show();
        statusLabel.setText("Caricamento richieste...");
        ApiClient.instance().get("/api/admin/requests", new TypeReference<List<AdminRequestDto>>() {})
                .thenAccept(list -> Platform.runLater(() -> {
                    rows.setAll(list == null ? List.<AdminRequestDto>of() : list);
                    latestUpdatedAt = list == null ? null
                            : list.stream()
                                    .map(a -> a.completedAt() != null ? a.completedAt() : a.createdAt())
                                    .filter(java.util.Objects::nonNull)
                                    .max(Comparator.naturalOrder())
                                    .orElse(Instant.now());
                    render();
                    statusLabel.setText((list == null ? 0 : list.size()) + " richieste");
                    loading.hide();
                }))
                .exceptionally(this::error);
    }

    private void render() {
        cardsContainer.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("Nessuna richiesta in corso");
            empty.setStyle("-fx-text-fill: #999;");
            cardsContainer.getChildren().add(empty);
            return;
        }
        for (AdminRequestDto r : rows) {
            cardsContainer.getChildren().add(buildCard(r));
        }
    }

    private VBox buildCard(AdminRequestDto r) {
        VBox card = new VBox(4);
        card.setStyle("-fx-padding: 10; -fx-background-color: #2a2a2a;"
                + " -fx-border-color: #444; -fx-background-radius: 4;");
        card.setPrefWidth(Region.USE_COMPUTED_SIZE);

        Label header = new Label(r.eventType() + "  ·  " + r.requestId());
        header.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");

        Label meta = new Label("ruolo=" + r.actingRole()
                + "  ·  building=" + r.buildingId()
                + "  ·  creato=" + (r.createdAt() == null ? "?" : r.createdAt())
                + (r.completedAt() == null ? "" : "  ·  completato=" + r.completedAt()));
        meta.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        String status = r.status() == null ? "" : r.status().toUpperCase();
        Label state = new Label();
        switch (status) {
            case "PENDING" -> {
                ProgressIndicator pi = new ProgressIndicator();
                pi.setMaxSize(20, 20);
                pi.setStyle("-fx-progress-color: #f39c12;");
                state.setText("in attesa di conferma…");
                state.setStyle("-fx-text-fill: #f39c12;");
                statusRow.getChildren().addAll(pi, state);
            }
            case "COMPLETED" -> {
                state.setText("✓ COMPLETED — " + readableResult(r));
                state.setStyle("-fx-text-fill: #2ecc71;");
                statusRow.getChildren().add(state);
            }
            case "FAILED" -> {
                state.setText("Operazione non confermata entro il timeout — riprova/riesamina"
                        + "  (reason: " + readableResult(r) + ")");
                state.setStyle("-fx-text-fill: #e74c3c;");
                statusRow.getChildren().add(state);
            }
            default -> {
                state.setText(status);
                state.setStyle("-fx-text-fill: #ccc;");
                statusRow.getChildren().add(state);
            }
        }

        card.getChildren().addAll(header, meta, statusRow);
        return card;
    }

    /** Extracts a human-readable message from {@code resultData}. */
    private static String readableResult(AdminRequestDto r) {
        String rd = r.resultData();
        if (rd == null || rd.isBlank()) return r.status();
        // Simple heuristic: trim surrounding quotes / braces; look for "reason":"…"
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = m.readTree(rd);
            if (node.has("reason")) return node.get("reason").asText();
            if (node.has("status")) return node.get("status").asText();
        } catch (Exception ignored) {}
        return rd.length() > 80 ? rd.substring(0, 80) + "…" : rd;
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