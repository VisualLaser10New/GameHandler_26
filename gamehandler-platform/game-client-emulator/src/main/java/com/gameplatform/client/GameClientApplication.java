package com.gameplatform.client;

import com.gameplatform.client.infrastructure.ui.MainView;
import javafx.application.Application;

/**
 * Punto di ingresso principale per l'emulatore del client di gioco.
 * <p>
 * Avvia l'interfaccia utente JavaFX delegando il bootstrap alla classe {@link MainView}.
 * L'esecuzione avviene tramite il metodo {@link Application#launch(Class, String...)}.
 * </p>
 *
 * @see MainView
 * @see Application
 */
public class GameClientApplication {

    /**
     * Metodo di avvio dell'applicazione. Avvia il runtime JavaFX e carica la vista principale
     * rappresentata da {@link MainView}.
     *
     * @param args argomenti passati da riga di comando; può essere un array vuoto ma non {@code null}
     * @see Application#launch(Class, String...)
     */
    public static void main(String[] args) {
        Application.launch(MainView.class, args);
    }
}
