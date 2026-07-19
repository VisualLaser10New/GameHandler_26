package com.gameplatform.client.infrastructure.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.Map;

/**
 * Componente JavaFX riutilizzabile che visualizza una classifica ordinata
 * di giocatori con i relativi punteggi.
 * <p>
 * I punteggi vengono mostrati in ordine decrescente (dal più alto al più
 * basso). Un messaggio di stato vuoto viene visualizzato quando non sono
 * disponibili punteggi.
 */
public class ScoreboardComponent extends VBox {
    private final Label titleLabel;

    /**
     * Costruisce un {@code ScoreboardComponent} vuoto con spaziatura
     * predefinita, sfondo scuro e titolo "Scoreboard".
     */
    public ScoreboardComponent() {
        setSpacing(4);
        setStyle("-fx-padding: 10; -fx-background-color: #2a2a2a; -fx-border-color: #555; -fx-border-radius: 4;");
        titleLabel = new Label("Scoreboard");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #eee; -fx-font-size: 14;");
        getChildren().add(titleLabel);
    }

    /**
     * Aggiorna i punteggi visualizzati, sostituendo le eventuali voci
     * esistenti.
     *
     * @param scores mappa dei nomi dei giocatori ai rispettivi punteggi;
     *               può essere {@code null} o vuota per mostrare il
     *               segnaposto "No scores yet"
     */
    public void updateScores(Map<String, Integer> scores) {
        getChildren().retainAll(titleLabel);

        if (scores == null || scores.isEmpty()) {
            Label empty = new Label("No scores yet");
            empty.setStyle("-fx-text-fill: #999;");
            getChildren().add(empty);
            return;
        }

        scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> {
                    Label scoreLabel = new Label(entry.getKey() + ": " + entry.getValue());
                    scoreLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 13;");
                    getChildren().add(scoreLabel);
                });
    }
}
