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
 * Rappresenta una partita di Monopoly gestita all'interno della piattaforma di gioco.
 *
 * <p>La classe incapsula lo stato di una sessione, i partecipanti, le risorse possedute da
 * ciascun giocatore, l'indice del turno corrente e la motivazione di eventuali interruzioni,
 * implementando il ciclo di vita definito da {@link GameLifecycle}.</p>
 *
 * @see GameLifecycle
 * @see GameSessionId
 * @see GameStatus
 * @see GameType
 * @see StopReason
 * @see UserId
 */
public class MonopolyGame implements GameLifecycle {
    private List<UserId> partecipants;
    private boolean running;
    private GameSessionId sessionId;
    private Map<UserId, Map<String, Integer>> resources;
    private int turnIndex;
    private StopReason stopReason;

    /**
     * Costruisce una nuova partita di Monopoly non avviata associata all'identificativo di
     * sessione fornito.
     *
     * <p>La partita viene creata con lista dei partecipanti vuota, nessuna risorsa inizializzata,
     * indice del turno pari a {@code 0} e senza alcuna motivazione di interruzione.</p>
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     * @throws NullPointerException se {@code sessionId} è {@code null}
     */
    public MonopolyGame(GameSessionId sessionId) {
        partecipants = new ArrayList<>();
        running = false;
        this.resources = new HashMap<>();
        this.turnIndex = 0;
        this.stopReason = null;
        this.sessionId = sessionId;
    }

    /**
     * Restituisce l'identificativo della sessione di gioco associata a questa partita.
     *
     * @return identificativo univoco della sessione; non è {@code null}
     */
    @Override
    public GameSessionId getSessionId() {
        return sessionId;
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * <p>Lo stesso riferimento della lista interna viene esposto, pertanto eventuali modifiche
     * alla lista restituita si riflettono sullo stato della partita.</p>
     *
     * @return lista dei partecipanti; non è {@code null}, può essere vuota se la partita
     *         non è stata ancora avviata
     * @see #setPartecipants(List)
     * @see #getParticipants()
     */
    public List<UserId> getPartecipants() {
        return partecipants;
    }

    /**
     * Sostituisce l'elenco dei partecipanti alla partita con quello fornito.
     *
     * @param partecipants nuova lista dei partecipanti; non deve essere {@code null}
     * @throws NullPointerException se {@code partecipants} è {@code null}
     * @see #getPartecipants()
     */
    public void setPartecipants(List<UserId> partecipants) {
        this.partecipants = partecipants;
    }

    /**
     * Restituisce la mappa delle risorse possedute dai giocatori.
     *
     * <p>Per ciascun giocatore la mappa interna associa il nome della risorsa al relativo valore
     * intero (ad esempio {@code "money"}).</p>
     *
     * @return mappa delle risorse per giocatore; non è {@code null}, può essere vuota prima
     *         dell'avvio della partita
     * @see #setResources(Map)
     */
    public Map<UserId, Map<String, Integer>> getResources() {
        return resources;
    }

    /**
     * Sostituisce l'intera mappa delle risorse possedute dai giocatori con quella fornita.
     *
     * @param resources nuova mappa delle risorse; non deve essere {@code null}
     * @throws NullPointerException se {@code resources} è {@code null}
     * @see #getResources()
     */
    public void setResources(Map<UserId, Map<String, Integer>> resources) {
        this.resources = resources;
    }

    /**
     * Restituisce l'indice del partecipante di cui è il turno corrente.
     *
     * @return indice del turno corrente, sempre maggiore o uguale a {@code 0}
     * @see #setTurnIndex(int)
     * @see #endTurn()
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * Imposta l'indice del partecipante di cui è il turno corrente.
     *
     * @param turnIndex nuovo indice del turno; deve essere maggiore o uguale a {@code 0}
     */
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    /**
     * Restituisce la motivazione che ha determinato l'interruzione della partita.
     *
     * @return motivazione di interruzione, o {@code null} se la partita è in corso o non è
     *         mai stata interrotta
     * @see #setStopReason(StopReason)
     * @see #stop(StopReason)
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta la motivazione di interruzione della partita.
     *
     * @param stopReason motivazione di interruzione; può essere {@code null}
     * @see #getStopReason()
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Avvia la partita con i partecipanti indicati, inizializzando le risorse di ogni giocatore.
     *
     * <p>Al momento dell'avvio ciascun partecipante riceve la risorsa {@code "money"} con valore
     * pari a {@code 1500}, l'indice del turno viene azzerato e la motivazione di interruzione
     * viene annullata.</p>
     *
     * @param participants elenco dei partecipanti con cui avviare la partita; non deve essere
     *                     {@code null} né contenere elementi {@code null}
     * @throws NullPointerException se {@code participants} è {@code null}
     * @see #stop(StopReason)
     * @see #pause()
     */
    @Override
    public void start(List<UserId> participants) {
        running = true;
        this.partecipants = participants;
        this.stopReason = null;

        this.resources.clear();
        for (UserId participant : participants) {
            Map<String, Integer> initial = new HashMap<>();
            initial.put("money", 1500);
            this.resources.put(participant, initial);
        }

        this.turnIndex = 0;
    }

    /**
     * Interrompe la partita in corso registrando la motivazione fornita.
     *
     * @param reason motivazione dell'interruzione; non deve essere {@code null}
     * @throws NullPointerException se {@code reason} è {@code null}
     * @see #getStopReason()
     * @see #pause()
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Aggiorna il valore di una risorsa di un giocatore sommandovi l'importo indicato.
     *
     * <p>Se la risorsa identificata dalla chiave non esiste per il giocatore, viene considerato
     * un valore di partenza pari a {@code 0} prima di applicare la variazione.</p>
     *
     * @param player giocatore proprietario della risorsa; non deve essere {@code null}
     * @param key    nome della risorsa da aggiornare; non deve essere {@code null}
     * @param val    importo da sommare al valore corrente, può essere negativo
     * @throws IllegalStateException se la partita non è in esecuzione o se il giocatore
     *                               non è tra i partecipanti
     * @throws NullPointerException se {@code player} o {@code key} è {@code null}
     */
    public void updateResource(UserId player, String key, int val) {
        if (!running) {
            throw new IllegalStateException("MonopolyGame is not running");
        }
        if (!resources.containsKey(player)) {
            throw new IllegalStateException("Player " + player + " not found");
        }

        Map<String, Integer> playerResources = resources.get(player);
        int newValue = playerResources.getOrDefault(key, 0) + val;
        playerResources.put(key, newValue);
    }

    /**
     * Passa al turno del partecipante successivo, avanzando ciclicamente tra i giocatori.
     *
     * <p>Al raggiungimento dell'ultimo partecipante l'indice riparte dal primo.</p>
     *
     * @throws IllegalStateException se la partita non è in esecuzione
     * @see #getTurnIndex()
     */
    public void endTurn() {
        if (!running) {
            throw new IllegalStateException("MonopolyGame is not running");
        }

        this.turnIndex = (this.turnIndex + 1) % partecipants.size();
    }

    /**
     * Restituisce l'elenco dei partecipanti alla partita.
     *
     * @return lista dei partecipanti; non è {@code null}, può essere vuota se la partita
     *         non è stata ancora avviata
     * @see #getPartecipants()
     */
    @Override
    public List<UserId> getParticipants() {
        return partecipants;
    }

    /**
     * Sospende la partita in corso senza registrarne la motivazione di interruzione.
     *
     * @see #resume()
     * @see #stop(StopReason)
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
     * Restituisce lo stato corrente della partita in base all'esecuzione.
     *
     * @return {@link GameStatus#IN_PROGRESS} se la partita è in esecuzione,
     *         {@link GameStatus#COMPLETED} altrimenti
     * @see GameStatus
     */
    @Override
    public GameStatus getStatus() {
        return running ? GameStatus.IN_PROGRESS : GameStatus.COMPLETED;
    }

    /**
     * Restituisce il tipo di gioco rappresentato da questa istanza.
     *
     * @return {@link GameType#MONOPOLY}
     * @see GameType
     */
    @Override
    public GameType getGameType() {
        return GameType.MONOPOLY;
    }

    /**
     * Restituisce il numero minimo di giocatori richiesto per avviare la partita.
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
}
