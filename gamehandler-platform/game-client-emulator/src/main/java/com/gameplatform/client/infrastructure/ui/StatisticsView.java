package com.gameplatform.client.infrastructure.ui;

import com.gameplatform.shared.dto.StatisticsDto;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * JavaFX view that displays aggregated game statistics.
 * <p>
 * Statistics are fetched asynchronously via {@code GET /api/statistics}
 * and rendered as cards, each showing the game type, total sessions,
 * average duration, and total reservations.
 */
public class StatisticsView {
    private final VBox root;
    private final Label titleLabel;
    private final VBox statsContainer;
    private final Label statusLabel;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public StatisticsView() {
        mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        root = new VBox(10);
        root.setStyle("-fx-padding: 20; -fx-background-color: #1e1e1e;");

        titleLabel = new Label("Game Statistics");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #eee;");

        statsContainer = new VBox(6);

        statusLabel = new Label("Loading statistics...");
        statusLabel.setStyle("-fx-text-fill: #aaa;");

        root.getChildren().addAll(titleLabel, statsContainer, statusLabel);
    }

    /**
     * Returns the root JavaFX node for this view.
     *
     * @return the statistics {@link Parent}
     */
    public Parent getView() {
        return root;
    }

    /**
     * Fetches statistics from the Local Server and renders them.
     * The request is sent asynchronously; results are applied on the
     * JavaFX Application Thread.
     */
    public void showStats() {
        statusLabel.setText("Loading...");
        try {
            String localServerUrl = System.getenv().getOrDefault("LOCAL_SERVER_URL", "https://localhost:8081");
            HttpClient client = com.gameplatform.client.infrastructure.security.HttpClientHelper.getHttpClient(localServerUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(localServerUrl + "/api/statistics"))
                    .GET()
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> Platform.runLater(() -> {
                        if (response.statusCode() == 200) {
                            try {
                                com.fasterxml.jackson.core.type.TypeReference<List<StatisticsDto>> typeRef =
                                        new com.fasterxml.jackson.core.type.TypeReference<>() {};
                                List<StatisticsDto> stats = mapper.readValue(response.body(), typeRef);
                                displayStats(stats);
                            } catch (Exception e) {
                                statusLabel.setText("Parse error: " + e.getMessage());
                            }
                        } else {
                            statusLabel.setText("Failed to load stats: " + response.statusCode());
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> statusLabel.setText("Connection error: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Renders the list of statistics as styled cards inside
     * {@code statsContainer}.
     *
     * @param stats the statistics to display, or {@code null} / empty
     */
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

            Label sessionsLabel = new Label("Sessions: " + s.totalSessions());
            sessionsLabel.setStyle("-fx-text-fill: #ddd;");

            Label durationLabel = new Label("Avg Duration: " + s.avgDuration() + "s");
            durationLabel.setStyle("-fx-text-fill: #ddd;");

            Label reservationsLabel = new Label("Reservations: " + s.totalReservations());
            reservationsLabel.setStyle("-fx-text-fill: #ddd;");

            card.getChildren().addAll(gameLabel, sessionsLabel, durationLabel, reservationsLabel);
            statsContainer.getChildren().add(card);
        }

        statusLabel.setText(stats.size() + " entries");
    }
}
