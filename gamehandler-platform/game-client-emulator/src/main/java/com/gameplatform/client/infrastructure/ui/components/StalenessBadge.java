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
 * Widget di stato visualizzato in basso a destra (o dove la vista lo
 * incorpora) che:
 * <ul>
 *   <li>mostra il timestamp "Dati aggiornati al: HH:mm:ss" costruito a
 *       partire da {@code max(updatedAt)} della vista corrente;</li>
 *   <li>aggiunge un badge giallo "in attesa di replica" quando
 *       {@code now - max(updatedAt) > staleThresholdMs} — configurabile
 *       tramite {@code ui.stale-threshold-ms} (default 300000 ms, 5 minuti).</li>
 * </ul>
 * La vista fornisce un {@link Supplier} che restituisce l'{@link Instant}
 * più recente tra le entità caricate (vuoto quando la vista non ha ancora dati).
 */
public final class StalenessBadge extends HBox {

    private static final DateTimeFormatter HOURS_MINUTES_SECONDS =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Label timestampLabel = new Label("Data updated at: —");
    private final Label staleBadge    = new Label("waiting for replication");
    private final Supplier<java.util.Optional<Instant>> latestUpdatedSupplier;
    private final long staleThresholdMs;

    /**
     * Costruisce un {@code StalenessBadge} con il fornitore di timestamp
     * e la soglia di obsolescenza specificati.
     *
     * @param latestUpdatedSupplier fornitore che restituisce l'{@link Instant}
     *                              più recente tra i dati correnti; può
     *                              restituire {@link Optional#empty()} se
     *                              non sono presenti dati
     * @param staleThresholdMs      soglia di obsolescenza in millisecondi;
     *                              deve essere un valore positivo
     */
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

    /**
     * Aggiorna il timestamp visualizzato e la visibilità del badge di
     * obsolescenza in base ai dati più recenti forniti dal supplier.
     * <p>
     * Se il supplier restituisce {@link Optional#empty()}, viene mostrato
     * il segnaposto "Data updated at: —" e il badge viene nascosto.
     * Se il supplier restituisce un {@link Instant}, il badge viene
     * mostrato quando la differenza tra l'istante corrente e quello
     * fornito supera la soglia di obsolescenza.
     * <p>
     * Se il metodo viene chiamato al di fuori del thread dell'applicazione
     * JavaFX, l'esecuzione viene reindirizzata automaticamente al thread
     * FX tramite {@link Platform#runLater(Runnable)}.
     */
    public void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }
        java.util.Optional<Instant> latest = latestUpdatedSupplier == null
                ? java.util.Optional.empty()
                : latestUpdatedSupplier.get();
        if (latest.isEmpty()) {
            timestampLabel.setText("Data updated at: —");
            staleBadge.setVisible(false);
            return;
        }
        Instant max = latest.get();
        timestampLabel.setText("Data updated at: "
                + LocalTime.ofInstant(max, ZoneId.systemDefault()).format(HOURS_MINUTES_SECONDS));
        long lag = Duration.between(max, Instant.now()).toMillis();
        staleBadge.setVisible(lag > staleThresholdMs);
    }
}