package com.gameplatform.client.infrastructure.ui.panels;

import javafx.scene.Parent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interfaccia comune per tutti i pannelli di emulazione dei giochi.
 * <p>
 * Ogni implementazione è responsabile del rendering dei controlli specifici del gioco
 * (es. pulsanti goal per il calciobalilla, controlli di mossa per gli scacchi)
 * e della reazione agli eventi del ciclo di vita (avvio/arresto della partita).
 */
public interface GamePanel {

    /**
     * Interfaccia funzionale utilizzata dai pannelli a turni per trasmettere
     * un cambio di turno agli altri emulatori partecipanti tramite MQTT.
     */
    @FunctionalInterface
    interface TurnPublisher {
        /**
         * Trasmette il cambio di turno all'emulatore remoto.
         *
         * @param turnIndex  il nuovo indice del turno (base 0) nella lista dei partecipanti
         * @param playerName il nome utente del giocatore a cui spetta il turno
         */
        void publish(int turnIndex, String playerName);
    }

    /**
     * Interfaccia funzionale utilizzata dai pannelli a scacchiera (attualmente Chess)
     * per trasmettere la mossa di un pezzo agli altri emulatori partecipanti tramite MQTT,
     * in modo che tutti i client mostrino lo stesso stato della scacchiera.
     */
    @FunctionalInterface
    interface MovePublisher {
        /**
         * Trasmette una mossa di un pezzo all'emulatore remoto.
         *
         * @param fromRow       riga di origine (base 0)
         * @param fromCol       colonna di origine (base 0)
         * @param toRow         riga di destinazione (base 0)
         * @param toCol         colonna di destinazione (base 0)
         * @param capturedPiece glifo Unicode del pezzo sulla cella di destinazione,
         *                      oppure {@code null} se la cella è vuota
         */
        void publish(int fromRow, int fromCol, int toRow, int toCol, String capturedPiece);
    }

    /**
     * Interfaccia funzionale utilizzata dai pannelli basati sul punteggio (es. Darts,
     * Foosball) per trasmettere un'istantanea del punteggio agli altri emulatori
     * partecipanti tramite MQTT, in modo che tutti i client mostrino lo stesso tabellone.
     */
    @FunctionalInterface
    interface ScorePublisher {
        /**
         * Trasmette un'istantanea completa dei punteggi all'emulatore remoto.
         *
         * @param scores un'istantanea completa delle entry giocatore {@literal ->} punteggio
         */
        void publish(java.util.Map<String, Integer> scores);
    }

    /**
     * Restituisce il nodo JavaFX radice per questo pannello.
     *
     * @return il {@link Parent} radice del pannello
     */
    Parent getView();

    /**
     * Invocato all'avvio della sessione di gioco.
     *
     * @param participants lista dei nomi utente dei partecipanti in ordine di sessione
     */
    void onGameStarted(List<String> participants);

    /**
     * Invocato al termine o all'abbandono della sessione di gioco.
     * Le implementazioni dovrebbero resettare lo stato interno.
     */
    void onGameStopped();

    /**
     * Inietta un callback che il pannello deve invocare ogni volta che il punteggio
     * di un partecipante cambia, in modo che la vista padre possa aggiornare il
     * {@code ScoreboardComponent} laterale condiviso. I pannelli senza punteggio
     * numerico possono mantenere l'implementazione predefinita vuota.
     *
     * @param scoreConsumer accetta una mappa di partecipante/nome {@literal ->} punteggio
     */
    default void setScoreConsumer(Consumer<Map<String, Integer>> scoreConsumer) {
        // no-op by default; panels with a score override this
    }

    /**
     * Inietta un publisher utilizzato dai pannelli basati sul punteggio (es. Darts,
     * Foosball) per trasmettere un'istantanea del punteggio agli altri emulatori
     * tramite MQTT. Può essere lasciato come implementazione predefinita vuota dai
     * pannelli che non gestiscono un punteggio.
     *
     * @param scorePublisher publisher per le istantanee del punteggio in uscita
     */
    default void setScorePublisher(ScorePublisher scorePublisher) {
        // no-op by default; score-based panels override this
    }

    /**
     * Invocato dalla vista padre quando arriva un messaggio MQTT remoto con il
     * punteggio, in modo che il pannello possa applicare i nuovi punteggi e aggiornare
     * la propria UI. I pannelli basati sul punteggio sovrascrivono questo metodo;
     * l'implementazione predefinita è vuota.
     *
     * @param scores un'istantanea completa delle entry giocatore {@literal ->} punteggio
     */
    default void onRemoteScore(Map<String, Integer> scores) {
        // no-op by default; score-based panels override this
    }

    /**
     * Inietta il contesto necessario per i pannelli multiplayer a turni
     * (Chess, Risk, Darts, Monopoly) per sincronizzare i turni tra gli emulatori.
     * Quando il giocatore locale termina il proprio turno, il pannello invoca
     * {@code turnPublisher.publish(...)}; la vista padre trasmette un messaggio MQTT
     * e ogni altro emulatore lo riceve tramite {@link #onRemoteTurnUpdate(int, String)}.
     * I pannelli non a turni possono mantenere l'implementazione predefinita vuota.
     *
     * @param turnPublisher publisher utilizzato per trasmettere i cambi di turno
     * @param currentUser   nome utente del giocatore locale, in modo che il pannello
     *                      sappia se è il turno del giocatore attivo
     */
    default void setTurnContext(TurnPublisher turnPublisher, String currentUser) {
        // no-op by default; turn-based panels override this
    }

    /**
     * Invocato dalla vista padre quando arriva un messaggio MQTT remoto di
     * aggiornamento turno, in modo che il pannello possa applicare il nuovo indice
     * del turno, aggiornare l'indicatore di turno e abilitare/disabilitare i propri
     * controlli di conseguenza. I pannelli a turni sovrascrivono questo metodo;
     * l'implementazione predefinita è vuota per i pannelli che non gestiscono turni.
     *
     * @param turnIndex  il nuovo indice del turno (base 0) nella lista dei partecipanti
     * @param playerName il nome utente del giocatore a cui spetta il turno
     */
    default void onRemoteTurnUpdate(int turnIndex, String playerName) {
        // no-op by default; turn-based panels override this
    }

    /**
     * Inietta un publisher utilizzato dai pannelli a scacchiera (attualmente Chess)
     * per trasmettere le mosse individuali dei pezzi agli altri emulatori.
     * Può essere lasciato come implementazione predefinita vuota dai pannelli
     * che non gestiscono una scacchiera.
     *
     * @param movePublisher publisher per le mosse in uscita
     */
    default void setMovePublisher(MovePublisher movePublisher) {
        // no-op by default; board-style panels override this
    }

    /**
     * Invocato dalla vista padre quando arriva un messaggio MQTT remoto di mossa,
     * in modo che il pannello possa applicare la mossa al proprio stato della
     * scacchiera e aggiornare la UI. I pannelli a scacchiera sovrascrivono questo
     * metodo; l'implementazione predefinita è vuota.
     *
     * @param fromRow       riga di origine (base 0)
     * @param fromCol       colonna di origine (base 0)
     * @param toRow         riga di destinazione (base 0)
     * @param toCol         colonna di destinazione (base 0)
     * @param capturedPiece glifo Unicode del pezzo catturato sulla cella di
     *                      destinazione, oppure {@code null} se la cella è vuota
     */
    default void onRemoteMove(int fromRow, int fromCol, int toRow, int toCol, String capturedPiece) {
        // no-op by default; board-style panels override this
    }
}
