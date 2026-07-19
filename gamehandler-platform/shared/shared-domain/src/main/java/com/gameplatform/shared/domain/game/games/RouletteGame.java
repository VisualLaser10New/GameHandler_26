package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.*;

/**
 * Rappresenta un gioco di roulette che aderisce al ciclo di vita definito da {@link GameLifecycle}.
 * Gestisce i partecipanti, le puntate effettuate su numeri specifici, l'avanzamento dei turni e
 * lo stato di esecuzione della sessione associata all'identificativo fornito.
 *
 * @see GameLifecycle
 * @see GameSessionId
 * @see GameType#ROULETTE
 */
public class RouletteGame implements GameLifecycle {
    private List<UserId> participants;
    private boolean running;
    private StopReason stopReason;
    private Map<UserId, Map<String, Integer>> bets;
    private int turnIndex;
    private GameSessionId sessionId;

    /**
     * Crea una nuova istanza di roulette non avviata associata all'identificativo di sessione indicato.
     * Inizializza partecipanti, puntate e stato interno ai valori predefiniti: nessun partecipante,
     * nessuna puntata, gioco non in esecuzione e indice del turno a zero.
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     */
    public RouletteGame(GameSessionId sessionId) {
        this.participants = new ArrayList<>();
        this.running = false;
        this.stopReason = null;
        this.bets = new HashMap<>();
        this.turnIndex = 0;
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco associata a questa roulette.
     *
     * @return l'identificativo di sessione; non è {@code null} se l'istanza è stata creata correttamente
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla roulette.
     *
     * @return la lista dei partecipanti; può essere vuota ma non è {@code null}
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Sostituisce l'elenco corrente dei partecipanti con quello indicato.
     *
     * @param participants nuova lista dei partecipanti; non deve essere {@code null}
     */
    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    /**
     * Restituisce la mappa delle puntate registrate, indicizzata per partecipante e per numero puntato.
     *
     * @return la mappa delle puntate; non è {@code null} e associa a ogni partecipante una mappa non {@code null}
     */
    public Map<UserId, Map<String, Integer>> getBets() {
        return bets;
    }

    /**
     * Sostituisce la mappa corrente delle puntate con quella indicata.
     *
     * @param bets nuova mappa delle puntate; non deve essere {@code null}
     */
    public void setBets(Map<UserId, Map<String, Integer>> bets) {
        this.bets = bets;
    }

    /**
     * Restituisce la motivazione che ha determinato l'arresto del gioco.
     *
     * @return la motivazione di arresto, o {@code null} se il gioco non è stato ancora fermato
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta la motivazione di arresto del gioco.
     *
     * @param stopReason motivazione dell'arresto; può essere {@code null}
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Imposta lo stato di esecuzione del gioco.
     *
     * @param running {@code true} se il gioco è in esecuzione, {@code false} altrimenti
     */
    public void setRunning(boolean running) { this.running = running; }

    /**
     * Restituisce l'indice del turno corrente all'interno dell'elenco dei partecipanti.
     *
     * @return l'indice del turno; è un valore compreso tra 0 e il numero di partecipanti meno uno
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * Imposta l'indice del turno corrente.
     *
     * @param turnIndex nuovo indice del turno; deve essere un valore non negativo
     */
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    /**
     * Avvia il gioco con l'elenco di partecipanti indicato, resettando lo stato di esecuzione,
     * le motivazioni di arresto e le puntate e inizializzando l'indice del turno a zero.
     * Ogni partecipante viene associato a una mappa di puntate vuota.
     *
     * @param participants lista dei partecipanti con cui avviare il gioco; non deve essere {@code null}
     * @see #stop(StopReason)
     * @see #endTurn()
     */
    @Override
    public void start(List<UserId> participants) {
        this.participants = participants;
        this.running = true;
        this.stopReason = null;
        this.bets.clear();
        this.turnIndex = 0;

        for (UserId player: participants) {
            bets.put(player, new HashMap<>());
        }
    }

    /**
     * Ferma il gioco registrando la motivazione indicata e ponendo lo stato di esecuzione a {@code false}.
     *
     * @param reason motivazione dell'arresto; non deve essere {@code null}
     * @see #start(List)
     */
    @Override
    public void stop(StopReason reason) {
        this.stopReason = reason;
        this.running = false;
    }

    /**
     * Sospende l'esecuzione del gioco ponendo lo stato di esecuzione a {@code false},
     * senza registrare una motivazione di arresto.
     *
     * @see #resume()
     */
    @Override
    public void pause() {
        this.running = false;
    }

    /**
     * Riprende l'esecuzione del gioco ponendo lo stato di esecuzione a {@code true}.
     *
     * @see #pause()
     */
    @Override
    public void resume() {
        this.running = true;
    }

    /**
     * Restituisce lo stato corrente del gioco in base allo stato di esecuzione.
     *
     * @return {@link GameStatus#IN_PROGRESS} se il gioco è in esecuzione,
     *         {@link GameStatus#COMPLETED} altrimenti
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco associato a questa istanza.
     *
     * @return {@link GameType#ROULETTE}
     */
    @Override
    public GameType getGameType() {
        return GameType.ROULETTE;
    }

    /**
     * Restituisce il numero minimo di giocatori necessario per avviare il gioco.
     *
     * @return il numero minimo di giocatori, pari a 1
     */
    @Override
    public int getMinPlayers() {
        return 1;
    }

    /**
     * Restituisce il numero massimo di giocatori supportato dal gioco.
     *
     * @return il numero massimo di giocatori, pari a 20
     */
    @Override
    public int getMaxPlayers() {
        return 20;
    }

    /**
     * Avanza al turno del partecipante successivo, aggiornando l'indice del turno con
     * avanzamento circolare sull'elenco dei partecipanti.
     *
     * @throws IllegalStateException se il gioco non è in esecuzione
     * @see #getTurnIndex()
     */
    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("RouletteGame in not running");
        }
        turnIndex = (turnIndex + 1) % participants.size();
    }

    /**
     * Registra o incrementa la puntata del partecipante indicato sul numero specificato.
     * Se il partecipante non aveva ancora puntato sul numero, la puntata viene inizializzata al valore indicato.
     *
     * @param player partecipante che effettua la puntata; non deve essere {@code null} e deve appartenere ai partecipanti
     * @param num numero su cui effettuare la puntata; non deve essere {@code null}
     * @param amount importo della puntata da aggiungere; deve essere un valore positivo
     * @throws IllegalStateException se il gioco non è in esecuzione o se il partecipante non è tra i partecipanti
     * @see #getBets()
     */
    public void placeBet(UserId player, String num, int amount) {
        if (!this.running) {
            throw new IllegalStateException("RouletteGame in not running");
        }
        if (!participants.contains(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerBets = bets.get(player);
        int newAmount = playerBets.getOrDefault(num, 0) + amount;
        playerBets.put(num, newAmount);
    }
}
