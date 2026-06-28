package com.gameplatform.client;

import com.gameplatform.client.infrastructure.ui.MainView;
import javafx.application.Application;

/**
 * Bootstrap entry point for the Game Client Emulator.
 * <p>
 * Delegates to {@link MainView} via standard JavaFX {@link Application#launch}.
 */
public class GameClientApplication {
    public static void main(String[] args) {
        Application.launch(MainView.class, args);
    }
}
