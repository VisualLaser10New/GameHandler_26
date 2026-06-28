package com.gameplatform.client.infrastructure.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * Reusable JavaFX component that displays a status bar with a coloured
 * indicator dot and descriptive text.
 * <p>
 * The dot colour changes based on keywords in the status text:
 * <ul>
 *   <li>Red for disconnected or error states</li>
 *   <li>Green for connected or online states</li>
 *   <li>Yellow/orange for all other states</li>
 * </ul>
 */
public class StatusBarComponent extends HBox {
    private final Label statusLabel;
    private final Label dotLabel;

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
     * Updates the status text and adjusts the indicator dot colour
     * according to the semantic content of the text.
     *
     * @param statusText the new status message (e.g. "Connected to MQTT",
     *                   "Disconnected", "Error: connection refused")
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
