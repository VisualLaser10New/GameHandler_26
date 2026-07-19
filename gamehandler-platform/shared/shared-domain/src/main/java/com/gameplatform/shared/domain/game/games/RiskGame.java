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
 * Rappresenta l'implementazione del gioco Risk all'interno della piattaforma,
 * gestendone il ciclo di vita, i partecipanti, le risorse per giocatore e i turni.
 * Espone le operazioni di avvio, arresto, pausa, ripresa e aggiornamento dello stato
 * secondo il contratto definito da {@link GameLifecycle}.
 *
 * @see GameLifecycle
 * @see GameSessionId
 * @see GameType
 */
public class RiskGame implements GameLifecycle {
    private List<UserId> participants;
    private StopReason stopReason;
    private Map<UserId, Map<String, Integer>> resources;
    private int turnIndex;
    private boolean running;
    private GameSessionId sessionId;

    /**
     * Costruisce una nuova istanza di gioco associata all'identificativo di sessione fornito,
     * inizializzando lo stato interno in modo coerente con un gioco non ancora avviato.
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     */
    public RiskGame(GameSessionId sessionId) {
        this.resources = new HashMap<>();
        this.turnIndex = 0;
        this.stopReason = null;
        this.running = false;
        this.participants = new ArrayList<>();
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * @return lista dei partecipanti; puo' essere vuota se il gioco non e' stato ancora avviato
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla partita.
     *
     * @param participants lista dei partecipanti da associare; non deve essere {@code null}
     */
    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    /**
     * Restituisce la motivazione che ha determinato l'arresto del gioco.
     *
     * @return la motivazione di arresto, oppure {@code null} se il gioco e' in esecuzione o non e' mai stato arrestato
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta la motivazione di arresto del gioco.
     *
     * @param stopReason motivazione dell'arresto; puo' essere {@code null}
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Restituisce la mappa delle risorse possedute dai giocatori.
     *
     * @return mappa che associa ciascun partecipante alle proprie risorse; non e' {@code null}
     */
    public Map<UserId, Map<String, Integer>> getResources() {
        return resources;
    }

    /**
     * Sostituisce l'intera mappa delle risorse possedute dai giocatori.
     *
     * @param resources nuova mappa delle risorse; non deve essere {@code null}
     */
    public void setResources(Map<UserId, Map<String, Integer>> resources) {
        this.resources = resources;
    }

    /**
     * Restituisce l'indice del partecipante cui e' attribuito il turno corrente.
     *
     * @return indice del turno corrente, pari a {@code 0} all'avvio del gioco
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * Imposta manualmente l'indice del turno corrente.
     *
     * @param turnIndex nuovo indice del turno; deve essere coerente con il numero di partecipanti
     */
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    /**
     * Imposta lo stato di esecuzione del gioco.
     *
     * @param running {@code true} se il gioco e' in esecuzione, {@code false} altrimenti
     */
    public void setRunning(boolean running) { this.running = running; }

    /**
     * Restituisce l'identificativo della sessione di gioco.
     *
     * @return identificativo univoco della sessione; non e' {@code null}
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Avvia la partita associando i partecipanti forniti e inizializzando le risorse di ciascuno.
     * Ogni giocatore riceve 5 armate e 0 territori; lo stato passa a in esecuzione e il turno corrente viene azzerato.
     *
     * @param participants lista dei partecipanti alla partita; non deve essere {@code null} ne' vuota
     * @see #getResources()
     * @see #getStatus()
     */
    @Override
    public void start(List<UserId> participants) {
        this.participants = participants;
        this.running = true;
        this.stopReason = null;
        this.resources.clear();
        for (UserId participant : participants) {
            Map<String, Integer> playerResources = new HashMap<>();
            playerResources.put("armies", 5);
            playerResources.put("territories", 0);
            resources.put(participant, playerResources);
        }

        this.turnIndex = 0;
    }

    /**
     * Arresta la partita in esecuzione registrando la motivazione fornita.
     *
     * @param reason motivazione dell'arresto; non deve essere {@code null}
     * @see #getStopReason()
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Sospende la partita in esecuzione senza registrare una motivazione di arresto.
     */
    @Override
    public void pause() {
        this.running = false;
    }

    /**
     * Riprende la partita precedentemente sospesa riportandola in esecuzione.
     */
    @Override
    public void resume() {
        this.running = true;
    }

    /**
     * Restituisce lo stato corrente della partita.
     *
     * @return {@link GameStatus#IN_PROGRESS} se il gioco e' in esecuzione, {@link GameStatus#COMPLETED} altrimenti
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco rappresentato da questa istanza.
     *
     * @return il valore {@link GameType#RISK}
     */
    @Override
    public GameType getGameType() {
        return GameType.RISK;
    }

    /**
     * Restituisce il numero minimo di giocatori necessario per avviare la partita.
     *
     * @return numero minimo di partecipanti, pari a {@code 2}
     */
    @Override
    public int getMinPlayers() {
        return 2;
    }

    /**
     * Restituisce il numero massimo di giocatori ammessi nella partita.
     *
     * @return numero massimo di partecipanti, pari a {@code 6}
     */
    @Override
    public int getMaxPlayers() {
        return 6;
    }

    /**
     * Aggiorna il valore di una risorsa del giocatore specificato sommando l'incremento fornito
     * al valore gia' posseduto; in assenza della risorsa viene considerato il valore {@code 0}.
     *
     * @param player partecipante cui aggiornare la risorsa; non deve essere {@code null}
     * @param key    chiave identificativa della risorsa; non deve essere {@code null}
     * @param val    valore da sommare alla risorsa, positivo o negativo
     * @throws IllegalStateException se il gioco non e' in esecuzione o se il giocatore non e' tra i partecipanti
     * @see #getResources()
     */
    public void updateResources(UserId player, String key, int val) {
        if (!this.running) {
            throw new IllegalStateException("RiskGame is not running");
        }
        if (!this.resources.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerResources = this.resources.get(player);
        int newValue = playerResources.getOrDefault(key, 0) + val;
        playerResources.put(key, newValue);
    }

    /**
     * Passa al turno del partecipante successivo, calcolato in modo circolare sull'elenco dei partecipanti.
     *
     * @throws IllegalStateException se il gioco non e' in esecuzione
     * @see #getTurnIndex()
     * @see #getParticipants()
     */
    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("RiskGame is not running");
        }

        this.turnIndex = (this.turnIndex + 1) % participants.size();
    }
}
