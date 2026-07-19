package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rappresenta una partita di freccette, implementando il ciclo di vita di un gioco
 * tramite l'interfaccia {@link GameLifecycle}.
 *
 * <p>Gestisce i partecipanti, i punteggi associati a ciascun giocatore, il turno corrente,
 * lo stato di esecuzione e la motivazione di arresto della sessione.</p>
 *
 * @see GameLifecycle
 * @see GameSessionId
 * @see GameStatus
 * @see GameType
 */
public class DartsGame implements GameLifecycle {
    private List<UserId> participants;
    private Map<UserId, Integer> scores;
    private boolean running;
    private int turnIndex;
    private GameSessionId sessionId;
    private StopReason stopReason;

    /**
     * Costruisce una nuova partita di freccette associata all'identificativo di sessione indicato.
     *
     * <p>Inizializza la partita in stato non avviato, con lista partecipanti e punteggi vuoti,
     * indice del turno a {@code 0} e motivazione di arresto non definita.</p>
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     */
    public DartsGame(GameSessionId sessionId) {
        participants = new ArrayList<>();
        scores = new HashMap<>();
        running = false;
        turnIndex = 0;
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco a cui appartiene questa partita.
     *
     * @return l'identificativo di sessione associato; non è {@code null}
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce la motivazione che ha determinato l'arresto della partita.
     *
     * @return la motivazione di arresto, o {@code null} se la partita non è stata ancora arrestata
     *         o è stata avviata nuovamente
     * @see #setStopReason(StopReason)
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta la motivazione di arresto della partita.
     *
     * @param stopReason motivazione dell'arresto; può essere {@code null}
     * @see #getStopReason()
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Restituisce l'indice del partecipante di cui è il turno corrente.
     *
     * @return indice, basato su zero, del turno corrente; è compreso tra {@code 0}
     *         e il numero di partecipanti meno uno
     * @see #setTurnIndex(int)
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * Imposta l'indice del turno corrente.
     *
     * @param turnIndex indice, basato su zero, del turno da impostare; deve essere coerente
     *                  con il numero di partecipanti
     * @see #getTurnIndex()
     */
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    /**
     * Restituisce la mappa dei punteggi associati a ciascun partecipante.
     *
     * @return mappa immutabile solo per riferimento, in cui la chiave è il partecipante
     *         e il valore è il relativo punteggio; non è {@code null}
     * @see #setScores(Map)
     */
    public Map<UserId, Integer> getScores() {
        return scores;
    }

    /**
     * Sostituisce l'intera mappa dei punteggi con quella fornita.
     *
     * @param scores nuova mappa dei punteggi, in cui la chiave è il partecipante e il valore
     *               è il relativo punteggio; non deve essere {@code null}
     * @see #getScores()
     */
    public void setScores(Map<UserId, Integer> scores) {
        this.scores = scores;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * @return lista dei partecipanti; può essere vuota se la partita non è stata ancora avviata
     * @see #setParticipants(List)
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla partita.
     *
     * @param participants nuova lista dei partecipanti; non deve essere {@code null}
     * @see #getParticipants()
     */
    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    /**
     * Imposta lo stato di esecuzione della partita.
     *
     * @param running {@code true} se la partita è in esecuzione, {@code false} altrimenti
     */
    public void setRunning(boolean running) { this.running = running; }

    /**
     * Avvia la partita con i partecipanti indicati, azzerando i punteggi e ripristinando
     * lo stato iniziale.
     *
     * <p>Reimposta l'indice del turno a {@code 0}, svuota e ricrea i punteggi impostando
     * a {@code 0} quello di ciascun partecipante, e annulla la motivazione di arresto.</p>
     *
     * @param participants elenco dei partecipanti con cui avviare la partita; non deve essere
     *                     {@code null} né vuoto
     */
    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.scores.clear();
        for (UserId userId : participants) {
            this.scores.put(userId, 0);
        }
        this.stopReason = null;
    }

    /**
     * Arresta la partita registrando la motivazione fornita.
     *
     * @param reason motivazione dell'arresto; non deve essere {@code null}
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Sospende l'esecuzione della partita senza registrarne la motivazione di arresto.
     *
     * <p>Lo stato passa a non in esecuzione, ma la partita può essere ripresa tramite
     * {@link #resume()}.</p>
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
     * Restituisce lo stato corrente della partita.
     *
     * @return {@link GameStatus#IN_PROGRESS} se la partita è in esecuzione,
     *         {@link GameStatus#COMPLETED} altrimenti
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco associato a questa partita.
     *
     * @return {@link GameType#DARTS}
     */
    @Override
    public GameType getGameType() {
        return GameType.DARTS;
    }

    /**
     * Restituisce il numero minimo di giocatori richiesto per la partita.
     *
     * @return il numero minimo di giocatori, pari a {@code 2}
     */
    @Override
    public int getMinPlayers() {
        return 2;
    }

    /**
     * Restituisce il numero massimo di giocatori ammesso per la partita.
     *
     * @return il numero massimo di giocatori, pari a {@code 3}
     */
    @Override
    public int getMaxPlayers() {
        return 3;
    }

    /**
     * Registra un incremento o un decremento di punteggio per il giocatore indicato.
     *
     * <p>Il punteggio risultante corrisponde alla somma del valore corrente e del delta fornito.</p>
     *
     * @param player giocatore di cui aggiornare il punteggio; non deve essere {@code null}
     * @param delta variazione da applicare al punteggio, positiva o negativa
     * @throws IllegalStateException se la partita non è in esecuzione o se il giocatore
     *                               indicato non è tra i partecipanti
     * @see #getScores()
     */
    public void recordScore(UserId player, int delta) {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        if (!scores.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " is not found");
        }

        int newScore = scores.get(player) + delta;
        this.scores.put(player, newScore);
    }

    /**
     * Passa al partecipante successivo, aggiornando ciclicamente l'indice del turno.
     *
     * <p>Al raggiungimento dell'ultimo partecipante, l'indice riparte dal primo.</p>
     *
     * @throws IllegalStateException se la partita non è in esecuzione
     * @see #getTurnIndex()
     */
    public void endTurn() {
        if (!running) {
            throw new IllegalStateException("Game is not running");
        }
        turnIndex = (turnIndex + 1) % participants.size();
    }
}
