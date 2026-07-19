package com.gameplatform.client.infrastructure.ui.components;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Pannello di errore riutilizzabile per la gestione globale degli errori.
 * <p>
 * Viene visualizzato quando una richiesta di vista fallisce in modo fatale
 * (offline / 5xx). Include un messaggio sintetico, la causa tecnica e un
 * pulsante di riprova collegato a un callback fornito dal chiamante.
 * Il pannello utilizza la stessa palette scura del resto del client
 * ({@code #1e1e1e/#333/#e74c3c}) per poter essere sostituito a una vista
 * normale senza salti visivi.
 */
public final class ErrorPane extends VBox {

    private final Label headline = new Label();
    private final Label detail   = new Label();
    private final Button retry    = new Button("Retry");

    /**
     * Costruisce un {@code ErrorPane} vuoto con layout centrato, spaziatura
     * predefinita e pulsante di riprova inizialmente nascosto.
     */
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

    /**
     * Mostra un messaggio di errore nel pannello.
     * <p>
     * Se il metodo viene chiamato al di fuori del thread dell'applicazione
     * JavaFX, l'esecuzione viene reindirizzata automaticamente al thread
     * FX tramite {@link Platform#runLater(Runnable)}.
     *
     * @param title          il titolo dell'errore; se {@code null} viene sostituito
     *                       con il testo predefinito "Error"
     * @param message        il messaggio descrittivo dell'errore; se {@code null}
     *                       viene sostituito con una stringa vuota
     * @param retryCallback  callback eseguito alla pressione del pulsante di
     *                       riprova; se {@code null} il pulsante viene nascosto
     */
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

    /**
     * Ripristina lo stato iniziale del pannello, cancellando titolo e
     * messaggio e nascondendo il pulsante di riprova.
     */
    public void clear() {
        headline.setText("");
        detail.setText("");
        retry.setVisible(false);
    }

    /**
     * Restituisce il pulsante di riprova per consentirne la personalizzazione
     * esterna (es. associazione di tasti rapidi o modifica dello stile).
     *
     * @return il pulsante di riprova {@link Button}
     */
    public Button retryButton() { return retry; }
}