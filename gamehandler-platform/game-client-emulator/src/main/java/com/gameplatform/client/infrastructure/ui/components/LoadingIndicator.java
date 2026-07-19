package com.gameplatform.client.infrastructure.ui.components;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

/**
 * Indicatore di caricamento riutilizzabile che sostituisce il segnaposto
 * testuale "Loading…".
 * <p>
 * Avvolge un {@link ProgressIndicator} JavaFX all'interno di un
 * {@link StackPane} in modo che la vista chiamante possa utilizzare
 * {@code setVisible(true/false)} sul wrapper senza dover gestire scambi
 * del grafo della scena. Il wrapper è non opaco per impostazione
 * predefinita (overlay trasparente), così il contenuto sottostante
 * rimane parzialmente visibile sotto lo spinner quando attivato.
 */
public final class LoadingIndicator extends StackPane {

    private final ProgressIndicator spinner;

    /**
     * Costruisce un {@code LoadingIndicator} con uno spinner di dimensione
     * massima 48x48 pixel e colore blu predefinito. Il componente è
     * inizialmente invisibile e non intercetta eventi del mouse.
     */
    public LoadingIndicator() {
        spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        spinner.setStyle("-fx-progress-color: #3498db;");
        getChildren().setAll(spinner);
        setVisible(false);
        setMouseTransparent(true);
        setStyle("-fx-background-color: transparent;");
    }

    /**
     * Rende visibile l'indicatore di caricamento.
     * <p>
     * Se il metodo viene chiamato al di fuori del thread dell'applicazione
     * JavaFX, l'esecuzione viene reindirizzata automaticamente al thread
     * FX tramite {@link Platform#runLater(Runnable)}.
     */
    public void show() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::show);
            return;
        }
        setVisible(true);
    }

    /**
     * Nasconde l'indicatore di caricamento.
     * <p>
     * Se il metodo viene chiamato al di fuori del thread dell'applicazione
     * JavaFX, l'esecuzione viene reindirizzata automaticamente al thread
     * FX tramite {@link Platform#runLater(Runnable)}.
     */
    public void hide() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::hide);
            return;
        }
        setVisible(false);
    }

    /**
     * Restituisce il nodo dello spinner per l'incorporamento in layout
     * che richiedono un nodo centrale (es. {@code BorderPane.setCenter}).
     *
     * @return il nodo {@link Node} contenente lo spinner
     */
    public Node asNode() {
        return spinner;
    }
}