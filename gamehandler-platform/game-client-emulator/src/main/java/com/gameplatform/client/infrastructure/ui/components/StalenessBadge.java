package com.gameplatform.client.infrastructure.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Status widget displayed bottom-right (or wherever the calling view
 * embeds it) that:
 * <ul>
 *   <li>shows the "Dati aggiornati al: HH:mm:ss" timestamp built from
 *       {@code max(updatedAt)} of the current view (PIANO §7.C line 759);</li>
 *   <li>adds a yellow "in attesa di replica" badge when
 *       {@code now - max(updatedAt) > staleThresholdMs} — configurable
 *       via {@code ui.stale-threshold-ms} (default 300000ms / 5 min).</li>
 * </ul>
 * The view supplies a {@link Supplier Optional<Instant>} returning the
 * most recent {@code updatedAt} of the loaded entities (empty when the
 * view has no data yet).
 */
public final class StalenessBadge extends HBox {

    private static final DateTimeFormatter HOURS_MINUTES_SECONDS =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Label timestampLabel = new Label("Dati aggiornati al: —");
    private final Label staleBadge    = new Label("in attesa di replica");
    private final Supplier<java.util.Optional<Instant>> latestUpdatedSupplier;
    private final long staleThresholdMs;

    public StalenessBadge(Supplier<java.util.Optional<Instant>> latestUpdatedSupplier,
                          long staleThresholdMs) {
        this.latestUpdatedSupplier = latestUpdatedSupplier;
        this.staleThresholdMs = staleThresholdMs;
        setAlignment(Pos.CENTER_RIGHT);
        setSpacing(10);
        setPadding(new Insets(2, 8, 2, 8));
        timestampLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11;");
        staleBadge.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;"
                + " -fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 11;");
        staleBadge.setVisible(false);
        getChildren().addAll(timestampLabel, staleBadge);
    }

    /** Should be called by the view every time its data set is refreshed. */
    public void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }
        java.util.Optional<Instant> latest = latestUpdatedSupplier == null
                ? java.util.Optional.empty()
                : latestUpdatedSupplier.get();
        if (latest.isEmpty()) {
            timestampLabel.setText("Dati aggiornati al: —");
            staleBadge.setVisible(false);
            return;
        }
        Instant max = latest.get();
        timestampLabel.setText("Dati aggiornati al: "
                + LocalTime.ofInstant(max, ZoneId.systemDefault()).format(HOURS_MINUTES_SECONDS));
        long lag = Duration.between(max, Instant.now()).toMillis();
        staleBadge.setVisible(lag > staleThresholdMs);
    }
}