package com.gameplatform.client.infrastructure.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * Componente JavaFX riutilizzabile che visualizza una barra di stato con
 * un punto indicatore colorato e un testo descrittivo.
 * <p>
 * Il colore del punto cambia in base a parole chiave nel testo di stato:
 * <ul>
 *   <li>Rosso per stati disconnessi o di errore</li>
 *   <li>Verde per stati connessi o online</li>
 *   <li>Giallo/arancione per tutti gli altri stati</li>
 * </ul>
 */
public class StatusBarComponent extends HBox {
    private final Label statusLabel;
    private final Label dotLabel;

    /**
     * Costruisce una {@code StatusBarComponent} con stato iniziale
     * "Disconnected" e punto indicatore rosso.
     */
    public StatusBarComponent() {
        setSpacing(8);
        setStyle("-fx-padding: 6 12; -fx-background-color: #1a1a1a; -fx-border-color: #333;");

        dotLabel = new Label("\u25CF");
        dotLabel.setStyle("-fx-text-fill: #e74c3c;");
        statusLabel = new Label("Disconnected");
        statusLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12;");
        getChildren().addAll(dotLabel, statusLabel);
    }

    /**
     * Aggiorna il testo di stato e regola il colore del punto indicatore
     * in base al contenuto semantico del testo.
     *
     * @param statusText il nuovo messaggio di stato (es. "Connected to MQTT",
     *                   "Disconnected", "Error: connection refused");
     *                   se {@code null} il punto indicatore viene
     *                   impostato sul colore rosso
     */
    public void updateStatus(String statusText) {
        statusLabel.setText(statusText);
        if (statusText == null || statusText.toLowerCase().contains("disconnect")
                || statusText.toLowerCase().contains("error")) {
            dotLabel.setStyle("-fx-text-fill: #e74c3c;");
        } else if (statusText.toLowerCase().contains("connect")
                || statusText.toLowerCase().contains("online")) {
            dotLabel.setStyle("-fx-text-fill: #2ecc71;");
        } else {
            dotLabel.setStyle("-fx-text-fill: #f39c12;");
        }
    }
}
