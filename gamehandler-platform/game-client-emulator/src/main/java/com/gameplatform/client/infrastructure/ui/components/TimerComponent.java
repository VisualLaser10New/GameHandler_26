package com.gameplatform.client.infrastructure.ui.components;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * Reusable JavaFX component that displays and manages an elapsed-time
 * counter in {@code MM:SS} format.
 * <p>
 * Uses a {@link Timeline} with a one-second tick for accurate display
 * updates. The timer can be started (resetting to zero) and stopped.
 */
public class TimerComponent extends HBox {
    private final Label timeLabel;
    private final Timeline timeline;
    private int elapsedSeconds;

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

    private void tick() {
        elapsedSeconds++;
        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;
        timeLabel.setText(String.format("%02d:%02d", minutes, seconds));
    }

    /**
     * Starts (or restarts) the timer from zero.
     * The display is reset to {@code 00:00} immediately.
     */
    public void startTimer() {
        elapsedSeconds = 0;
        timeLabel.setText("00:00");
        timeline.playFromStart();
    }

    /**
     * Stops the timer without resetting the elapsed time.
     */
    public void stopTimer() {
        timeline.stop();
    }

    /**
     * Resumes the timer from where it was stopped, without resetting
     * the elapsed time. Use this after {@link #stopTimer()} to continue
     * counting from the last value (e.g. after a pause).
     */
    public void resumeTimer() {
        timeline.play();
    }
}
