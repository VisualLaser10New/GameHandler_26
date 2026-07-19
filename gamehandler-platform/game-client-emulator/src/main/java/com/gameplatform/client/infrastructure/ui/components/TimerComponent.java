package com.gameplatform.client.infrastructure.ui.components;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * Componente JavaFX riutilizzabile che visualizza e gestisce un
 * contatore di tempo trascorso in formato {@code MM:SS}.
 * <p>
 * Utilizza un {@link Timeline} con un tick al secondo per aggiornamenti
 * precisi del display. Il timer può essere avviato (con reset a zero)
 * e fermato.
 */
public class TimerComponent extends HBox {
    private final Label timeLabel;
    private final Timeline timeline;
    private int elapsedSeconds;

    /**
     * Costruisce un {@code TimerComponent} con display iniziale
     * {@code 00:00} e timeline configurata per aggiornamenti ogni
     * secondo. Il timer non viene avviato automaticamente.
     */
    public TimerComponent() {
        setSpacing(6);
        setStyle("-fx-padding: 6; -fx-background-color: #1e1e1e; -fx-border-color: #444; -fx-border-radius: 4;");

        Label icon = new Label("\u23F1");
        icon.setStyle("-fx-text-fill: #eee;");
        timeLabel = new Label("00:00");
        timeLabel.setStyle("-fx-text-fill: #eee; -fx-font-size: 16; -fx-font-family: monospace;");
        getChildren().addAll(icon, timeLabel);

        elapsedSeconds = 0;
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Incrementa il contatore dei secondi trascorsi e aggiorna il
     * display nel formato {@code MM:SS}.
     */
    private void tick() {
        elapsedSeconds++;
        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;
        timeLabel.setText(String.format("%02d:%02d", minutes, seconds));
    }

    /**
     * Avvia (o riavvia) il timer azzerando il tempo trascorso.
     * Il display viene reimpostato immediatamente a {@code 00:00}.
     */
    public void startTimer() {
        elapsedSeconds = 0;
        timeLabel.setText("00:00");
        timeline.playFromStart();
    }

    /**
     * Ferma il timer senza resettare il tempo trascorso.
     * Utilizzare {@link #resumeTimer()} per riprendere la
     * conta da dove era stata interrotta.
     */
    public void stopTimer() {
        timeline.stop();
    }

    /**
     * Riprende il timer dal punto in cui era stato fermato, senza
     * resettare il tempo trascorso. Da utilizzare dopo
     * {@link #stopTimer()} per continuare la conta dal valore
     * precedente (es. dopo una pausa).
     */
    public void resumeTimer() {
        timeline.play();
    }
}
