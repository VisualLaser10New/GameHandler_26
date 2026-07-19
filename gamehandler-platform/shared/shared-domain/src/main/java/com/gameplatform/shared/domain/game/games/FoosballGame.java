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
 * Rappresenta una partita di calcio balilla gestita nel dominio condiviso della piattaforma.
 *
 * <p>La classe modella il ciclo di vita di una sessione di gioco (avvio, pausa, ripresa e
 * arresto), il tracciamento dei partecipanti e l'aggiornamento dei punteggi dei singoli
 * giocatori. Implementa {@link GameLifecycle} ed espone i metodi necessari a determinare
 * lo stato corrente e i limiti di giocatori ammessi.</p>
 *
 * @see GameLifecycle
 * @see GameType#FOOSBALL
 */
public class FoosballGame implements GameLifecycle {
    private List<UserId> participants;
    private Map<UserId, Integer> scores;
    private GameSessionId sessionId;
    private StopReason stopReason;
    private boolean running;

    /**
     * Costruisce una nuova partita di calcio balilla non avviata e senza partecipanti.
     *
     * <p>Inizializza lo stato interno della partita: la lista dei partecipanti vuota, i
     * punteggi vuoti, lo stato di esecuzione a {@code false} e il motivo di arresto a
     * {@code null}. La partita deve essere avviata tramite {@link #start(List)} prima di
     * poter registrare punteggi.</p>
     *
     * @param sessionId l'identificativo univoco della sessione di gioco; non deve essere {@code null}
     * @see #start(List)
     */
    public FoosballGame(GameSessionId sessionId) {
        this.participants = new ArrayList<>();
        this.running = false;
        this.stopReason = null;
        this.scores = new HashMap<>();
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco associata a questa partita.
     *
     * @return l'identificativo univoco della sessione; non è {@code null}
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce il motivo che ha determinato l'arresto della partita.
     *
     * @return il motivo di arresto se la partita è stata fermata, oppure {@code null} se la
     *         partita è in esecuzione o non è mai stata arrestata
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta il motivo che ha determinato l'arresto della partita.
     *
     * @param stopReason il motivo di arresto da associare alla partita; può essere {@code null}
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Restituisce la mappa dei punteggi associati a ciascun partecipante.
     *
     * @return la mappa che associa ogni {@link UserId} al proprio punteggio; non è {@code null},
     *         può essere vuota se la partita non è ancora stata avviata
     */
    public Map<UserId, Integer> getScores() {
        return scores;
    }

    /**
     * Sostituisce l'intera mappa dei punteggi con quella fornita.
     *
     * @param scores la nuova mappa dei punteggi da associare alla partita; non deve essere {@code null}
     */
    public void setScores(Map<UserId, Integer> scores) {
        this.scores = scores;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * @return la lista degli identificativi dei giocatori; non è {@code null}, può essere vuota
     *         prima dell'avvio della partita
     * @see #start(List)
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla partita.
     *
     * @param participants la lista degli identificativi dei giocatori da associare; non deve
     *                     essere {@code null}
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
     * Avvia la partita registrando i partecipanti e azzerando i punteggi.
     *
     * <p>La partita passa in stato di esecuzione, la lista dei partecipanti viene sostituita
     * con quella fornita e il punteggio di ciascun giocatore viene inizializzato a {@code 0}.
     * Il motivo di arresto viene azzerato.</p>
     *
     * @param participants l'elenco dei giocatori che prendono parte alla partita; non deve essere
     *                     {@code null} e i suoi elementi non devono essere {@code null}
     * @see #getMinPlayers()
     * @see #getMaxPlayers()
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
     * Arresta la partita in esecuzione registrandone il motivo.
     *
     * <p>La partita passa fuori dallo stato di esecuzione e il motivo di arresto viene
     * associato alla sessione.</p>
     *
     * @param reason il motivo che ha determinato l'arresto della partita; non deve essere {@code null}
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Sospende temporaneamente la partita senza registrarne il motivo di arresto.
     *
     * <p>La partita esce dallo stato di esecuzione mantenendo invariati partecipanti e
     * punteggi, ed è riprendibile tramite {@link #resume()}.</p>
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
     * <p>La partita torna nello stato di esecuzione mantenendo invariati partecipanti e
     * punteggi.</p>
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
     *         {@link GameStatus#COMPLETED} se è sospesa o arrestata
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco rappresentato da questa partita.
     *
     * @return {@link GameType#FOOSBALL} per tutte le istanze di questa classe
     */
    @Override
    public GameType getGameType() {
        return GameType.FOOSBALL;
    }

    /**
     * Restituisce il numero minimo di giocatori richiesto per la partita.
     *
     * @return il numero minimo di partecipanti, pari a {@code 2}
     */
    @Override
    public int getMinPlayers() {
        return 2;
    }

    /**
     * Restituisce il numero massimo di giocatori ammessi per la partita.
     *
     * @return il numero massimo di partecipanti, pari a {@code 4}
     */
    @Override
    public int getMaxPlayers() {
        return 4;
    }

    /**
     * Registra una variazione di punteggio per il giocatore indicato durante la partita.
     *
     * <p>Aggiorna il punteggio del giocatore sommando il valore fornito al punteggio attuale.
     * L'operazione è consentita esclusivamente mentre la partita è in esecuzione e solo per i
     * giocatori che vi prendono parte.</p>
     *
     * @param player il giocatore cui applicare la variazione; non deve essere {@code null}
     * @param delta  la variazione di punteggio da applicare; può essere positiva, negativa o {@code 0}
     * @throws IllegalStateException se la partita non è in esecuzione
     * @throws IllegalStateException se il giocatore indicato non partecipa alla partita
     * @see #start(List)
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
        scores.put(player, newScore);
    }
}
