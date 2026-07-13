package com.gameplatform.client.infrastructure.ui.components;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Reusable error pane for the global error handler (PIANO §7.C line 757).
 * <p>
 * Rendered when a view request fails fatally (offline / 5xx). Includes a
 * short message, the technical cause and a Retry button wired to a
 * caller-provided callback. The pane intentionally uses the same dark
 * theme palette ({@code #1e1e1e/#333/#e74c3c}) as the rest of the
 * client so it can be swapped in for a normal view with no visual jolt.
 */
public final class ErrorPane extends VBox {

    private final Label headline = new Label();
    private final Label detail   = new Label();
    private final Button retry    = new Button("Retry");

    public ErrorPane() {
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setStyle("-fx-padding: 40; -fx-background-color: #1e1e1e;");
        headline.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        detail.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12; -fx-wrap-text: true;");
        detail.setMaxWidth(520);
        retry.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;"
                + " -fx-padding: 8 24; -fx-background-radius: 4;");
        getChildren().addAll(headline, detail, retry);
        retry.setVisible(false);
    }

    /** Renders an error. {@code retryCallback} may be {@code null} to hide the button. */
    public void show(String title, String message, Runnable retryCallback) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(title, message, retryCallback));
            return;
        }
        headline.setText(title == null ? "Error" : title);
        detail.setText(message == null ? "" : message);
        if (retryCallback != null) {
            retry.setOnAction(e -> retryCallback.run());
            retry.setVisible(true);
        } else {
            retry.setVisible(false);
        }
    }

    /** Clears the pane for reuse. */
    public void clear() {
        headline.setText("");
        detail.setText("");
        retry.setVisible(false);
    }

    public Button retryButton() { return retry; }
}