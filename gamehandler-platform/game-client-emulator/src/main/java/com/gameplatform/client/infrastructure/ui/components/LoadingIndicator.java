package com.gameplatform.client.infrastructure.ui.components;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

/**
 * Reusable loading indicator replacing the "Loading…" textual placeholder
 * mandated by PIANO §7.C line 758.
 * <p>
 * Wraps a JavaFX {@link ProgressIndicator} inside a {@link StackPane} so
 * the calling view can {@code setVisible(true/false)} on the wrapper
 * without having to manage scene-graph swaps. The wrapper is
 * non-opaque by default (transparent overlay) so the underlying content
 * remains partially visible underneath the spinner when toggled on.
 */
public final class LoadingIndicator extends StackPane {

    private final ProgressIndicator spinner;

    public LoadingIndicator() {
        spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        spinner.setStyle("-fx-progress-color: #3498db;");
        getChildren().setAll(spinner);
        setVisible(false);
        setMouseTransparent(true);
        setStyle("-fx-background-color: transparent;");
    }

    public void show() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::show);
            return;
        }
        setVisible(true);
    }

    public void hide() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::hide);
            return;
        }
        setVisible(false);
    }

    /** Convenience: returns the Node for embedding in {@code BorderPane.setCenter}. */
    public Node asNode() {
        return spinner;
    }
}