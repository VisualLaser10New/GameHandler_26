package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.client.infrastructure.rest.ApiClient;
import com.gameplatform.shared.dto.StatisticsDto;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Vista delle statistiche aggregate di gioco.
 * <p>
 * Recupera i dati tramite {@code GET /api/statistics} dal
 * {@code StatisticsController} locale, aggregati per edificio.
 * La vista è disponibile per ogni utente autenticato.
 */
public class StatisticsView {
    private final VBox root;
    private final Label titleLabel;
    private final VBox statsContainer;
    private final Label statusLabel;

    /**
     * Costruisce la vista delle statistiche aggregate.
     * <p>
     * Inizializza il titolo, il contenitore per le schede delle
     * statistiche e l'etichetta di stato.
     */
    public StatisticsView() {
        VBox content = new VBox(10);

        titleLabel = new Label("Game Statistics (aggregated)");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        statsContainer = new VBox(6);
        statusLabel = new Label("Loading statistics...");
        statusLabel.setStyle("-fx-text-fill: #aaa;");

        content.getChildren().addAll(titleLabel, statsContainer, statusLabel);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        root = new VBox(scrollPane);
        root.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");
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
     * Carica e mostra le statistiche aggregate.
     * <p>
     * Effettua una chiamata asincrona {@code GET /api/statistics} e
     * renderizza ogni {@link StatisticsDto} come scheda informativa
     * con tipo di gioco, edificio, sessioni, durata media e prenotazioni.
     */
    public void showStats() {
        statusLabel.setText("Loading...");
        ApiClient.instance().get("/api/statistics", new TypeReference<List<StatisticsDto>>() {})
                .thenAccept(stats -> Platform.runLater(() -> displayStats(stats)))
                .exceptionally(ex -> { Platform.runLater(() -> {
                    Throwable t = ex;
                    while (t.getCause() != null) t = t.getCause();
                    statusLabel.setText("Connection error: " + t.getMessage());}); return null; });
    }

    private void displayStats(List<StatisticsDto> stats) {
        statsContainer.getChildren().clear();
        if (stats == null || stats.isEmpty()) {
            Label empty = new Label("No statistics available");
            empty.setStyle("-fx-text-fill: #999;");
            statsContainer.getChildren().add(empty);
            statusLabel.setText("0 entries");
            return;
        }
        for (StatisticsDto s : stats) {
            VBox card = new VBox(4);
            card.setStyle("-fx-padding: 10; -fx-background-color: #2a2a2a; -fx-border-color: #444; -fx-border-radius: 4;");
            Label gameLabel = new Label("Game: " + s.gameType());
            gameLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
            Label buildingLabel = new Label("Building: " + (s.buildingId() == null ? "—" : s.buildingId()));
            buildingLabel.setStyle("-fx-text-fill: #ddd;");
            Label sessionsLabel = new Label("Sessions: " + (s.totalSessions() == null ? 0 : s.totalSessions()));
            sessionsLabel.setStyle("-fx-text-fill: #ddd;");
            Label durationLabel = new Label("Avg Duration: " + (s.avgDuration() == null ? 0 : s.avgDuration()) + "s");
            durationLabel.setStyle("-fx-text-fill: #ddd;");
            Label reservationsLabel = new Label("Reservations: " + (s.totalReservations() == null ? 0 : s.totalReservations()));
            reservationsLabel.setStyle("-fx-text-fill: #ddd;");
            card.getChildren().addAll(gameLabel, buildingLabel, sessionsLabel, durationLabel, reservationsLabel);
            statsContainer.getChildren().add(card);
        }
        statusLabel.setText(stats.size() + " entries");
    }
}