package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.*;

/**
 * Rappresenta una slot machine come implementazione concreta del ciclo di vita di un gioco.
 *
 * <p>Il gioco ammette un solo partecipante (minimo e massimo 1 giocatore) e consente di
 * effettuare lanci dei rulli, registrare punteggi e gestire gli stati di avvio, pausa e
 * arresto della sessione di gioco.</p>
 *
 * @see com.gameplatform.shared.domain.game.GameLifecycle
 */
public class SlotMachineGame implements GameLifecycle {
    private List<UserId> participants;
    private StopReason stopReason;
    private Map<UserId, Integer> scores;
    private boolean running;
    private GameSessionId sessionId;

    /**
     * Costruisce una nuova slot machine associata all'identificativo di sessione fornito.
     *
     * <p>La partita viene creata non in esecuzione, senza partecipanti, senza punteggi e con
     * il motivo di arresto non ancora definito.</p>
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     */
    public SlotMachineGame(GameSessionId sessionId) {
        this.participants = new ArrayList<UserId>();
        this.stopReason = null;
        this.scores = new HashMap<UserId, Integer>();
        this.running = false;
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * @return lista dei partecipanti; non è {@code null}, può essere vuota se la partita
     *         non è stata ancora avviata
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla partita.
     *
     * @param participants lista dei partecipanti da associare; non deve essere {@code null}
     * @see #start(List)
     * @see #getParticipants()
     */
    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    /**
     * Restituisce il motivo dell'ultimo arresto della partita.
     *
     * @return motivo di arresto della partita, oppure {@code null} se la partita non è stata
     *         ancora arrestata o è attiva
     * @see StopReason
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta il motivo dell'arresto della partita.
     *
     * @param stopReason motivo dell'arresto da registrare; può essere {@code null}
     * @see #stop(StopReason)
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Restituisce la mappa dei punteggi associati a ciascun partecipante.
     *
     * @return mappa che associa ogni partecipante al proprio punteggio; non è {@code null},
     *         può essere vuota prima dell'avvio della partita
     */
    public Map<UserId, Integer> getScores() {
        return scores;
    }

    /**
     * Sostituisce l'intera mappa dei punteggi dei partecipanti.
     *
     * @param scores nuova mappa dei punteggi; non deve essere {@code null}
     */
    public void setScores(Map<UserId, Integer> scores) {
        this.scores = scores;
    }

    /**
     * Imposta lo stato di esecuzione della partita.
     *
     * @param running {@code true} se la partita è in esecuzione, {@code false} altrimenti
     */
    public void setRunning(boolean running) { this.running = running; }

    /**
     * Restituisce l'identificativo univoco della sessione di gioco.
     *
     * @return identificativo della sessione; non è {@code null}
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Avvia la partita con i partecipanti indicati, inizializzando i punteggi a zero e
     * azzerando un eventuale precedente motivo di arresto.
     *
     * <p>Al termine dell'esecuzione la partita risulta in esecuzione e i punteggi di tutti i
     * partecipanti sono impostati a {@code 0}.</p>
     *
     * @param participants elenco dei partecipanti con cui avviare la partita; non deve essere
     *                     {@code null}, una lista vuota comporta l'assenza di punteggi iniziali
     */
    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
        this.scores.clear();
        for (UserId participant : participants) {
            this.scores.put(participant, 0);
        }
    }

    /**
     * Arresta la partita registrando il motivo dell'arresto.
     *
     * @param reason motivo dell'arresto della partita; non deve essere {@code null}
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Sospende l'esecuzione della partita senza registrare un motivo di arresto definitivo.
     *
     * <p>La partita può essere ripresa tramite {@link #resume()}.</p>
     *
     * @see #resume()
     */
    @Override
    public void pause() {
        this.running = false;
    }

    /**
     * Riprende l'esecuzione di una partita precedentemente sospesa.
     *
     * @see #pause()
     */
    @Override
    public void resume() {
        this.running = true;
    }

    /**
     * Restituisce lo stato corrente della partita in base al flag di esecuzione.
     *
     * @return {@link GameStatus#IN_PROGRESS} se la partita è in esecuzione,
     *         {@link GameStatus#COMPLETED} altrimenti
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco rappresentato da questa istanza.
     *
     * @return {@link GameType#SLOT_MACHINE}
     */
    @Override
    public GameType getGameType() {
        return GameType.SLOT_MACHINE;
    }

    /**
     * Restituisce il numero minimo di giocatori richiesto dalla slot machine.
     *
     * @return numero minimo di giocatori, pari a {@code 1}
     */
    @Override
    public int getMinPlayers() {
        return 1;
    }

    /**
     * Restituisce il numero massimo di giocatori supportato dalla slot machine.
     *
     * @return numero massimo di giocatori, pari a {@code 1}
     */
    @Override
    public int getMaxPlayers() {
        return 1;
    }

    /**
     * Registra il punteggio di un partecipante, sostituendo il valore precedentemente associato.
     *
     * @param player partecipante di cui registrare il punteggio; non deve essere {@code null}
     * @param score  punteggio da assegnare; può essere qualsiasi valore intero, incluso negativo
     * @throws IllegalStateException se la partita non è in esecuzione oppure se il partecipante
     *                               indicato non è presente tra i giocatori della partita
     * @see #spin(UserId)
     */
    public void recordScore(UserId player, int score) {
        if (!this.running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!this.scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }
        this.scores.put(player, score);
    }

    private static final String[] SYMBOLS = {"CHERRY", "LEMON", "ORANGE", "PLUM", "BELL", "SEVEN"};
    private static final Random RANDOM = new Random();

    private String lastReel1;
    private String lastReel2;
    private String lastReel3;

    /**
     * Effettua un lancio dei rulli per il partecipante indicato, aggiornando il suo punteggio
     * in base alla combinazione ottenuta.
     *
     * <p>Il punteggio del partecipante viene incrementato del valore del pagamento calcolato
     * dalla combinazione risultante.</p>
     *
     * @param player partecipante che effettua il lancio; non deve essere {@code null}
     * @throws IllegalStateException se la partita non è in esecuzione oppure se il partecipante
     *                               indicato non è presente tra i giocatori della partita
     * @see #calculatePayout(String, String, String)
     * @see #getLastReel1()
     * @see #getLastReel2()
     * @see #getLastReel3()
     */
    public void spin(UserId player) {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }

        lastReel1 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        lastReel2 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        lastReel3 = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];

        int points = calculatePayout(lastReel1, lastReel2, lastReel3);
        scores.put(player, scores.get(player) + points);
    }

    /**
     * Calcola il pagamento associato a una combinazione di tre simboli dei rulli.
     *
     * @param r1 primo simbolo della combinazione; non deve essere {@code null}
     * @param r2 secondo simbolo della combinazione; non deve essere {@code null}
     * @param r3 terzo simbolo della combinazione; non deve essere {@code null}
     * @return {@code 100} se i tre simboli coincidono, {@code 10} se almeno due simboli
     *         coincidono, {@code 0} negli altri casi
     */
    private int calculatePayout(String r1, String r2, String r3) {
        if (r1.equals(r2) && r2.equals(r3)) {
            return 100;
        }
        if (r1.equals(r2) || r2.equals(r3) || r1.equals(r3)) {
            return 10;
        }
        return 0;
    }

    /**
     * Restituisce il simbolo ottenuto sul primo rullo nell'ultimo lancio effettuato.
     *
     * @return simbolo dell'ultimo rullo, oppure {@code null} se non è ancora stato effettuato
     *         alcun lancio
     * @see #spin(UserId)
     */
    public String getLastReel1() { return lastReel1; }

    /**
     * Restituisce il simbolo ottenuto sul secondo rullo nell'ultimo lancio effettuato.
     *
     * @return simbolo dell'ultimo rullo, oppure {@code null} se non è ancora stato effettuato
     *         alcun lancio
     * @see #spin(UserId)
     */
    public String getLastReel2() { return lastReel2; }

    /**
     * Restituisce il simbolo ottenuto sul terzo rullo nell'ultimo lancio effettuato.
     *
     * @return simbolo dell'ultimo rullo, oppure {@code null} se non è ancora stato effettuato
     *         alcun lancio
     * @see #spin(UserId)
     */
    public String getLastReel3() { return lastReel3; }
}
