package com.gameplatform.shared.domain.game.games;

import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.UserId;

import java.util.ArrayList;
import java.util.List;

/**
 * Rappresenta una partita di scacchi gestita all'interno della piattaforma.
 *
 * <p>La classe modella il ciclo di vita di una partita (avvio, pausa, ripresa, stop),
 * lo stato della scacchiera, i partecipanti e la gestione dei turni di gioco.
 * Supporta esattamente due giocatori e mantiene l'identificativo di sessione
 * associato alla partita.</p>
 *
 * @see GameLifecycle
 * @see GameSessionId
 * @see GameType#CHESS
 */
public class ChessGame implements GameLifecycle {
    private boolean running;
    private List<UserId> participants;
    private StopReason stopReason;
    private String boardState;
    private int turnIndex;
    private GameSessionId sessionId;

    /**
     * Crea una nuova partita di scacchi associata all'identificativo di sessione indicato.
     *
     * <p>La partita viene inizializzata non in esecuzione, con una lista di partecipanti
     * vuota, senza motivo di arresto, con indice del turno pari a {@code 0} e con lo
     * stato della scacchiera impostato allo stato iniziale.</p>
     *
     * @param sessionId identificativo univoco della sessione di gioco; non deve essere {@code null}
     * @see #initialBoard()
     */
    public ChessGame(GameSessionId sessionId) {
        this.running = false;
        this.participants = new ArrayList<>();
        this.boardState = this.initialBoard();
        this.stopReason = null;
        this.turnIndex = 0;
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
     * @return lista dei giocatori partecipanti; non è {@code null}, può essere vuota
     *         se la partita non è ancora stata avviata
     * @see #setParticipants(List)
     */
    @Override
    public List<UserId> getParticipants() {
        return participants;
    }

    /**
     * Imposta l'elenco dei partecipanti alla partita.
     *
     * @param participants lista dei giocatori partecipanti; non deve essere {@code null}
     * @see #getParticipants()
     */
    public void setParticipants(List<UserId> participants) {
        this.participants = participants;
    }

    /**
     * Restituisce il motivo che ha determinato l'arresto della partita.
     *
     * @return motivo di arresto della partita; è {@code null} se la partita è in esecuzione
     *         o non è stata ancora arrestata
     * @see #setStopReason(StopReason)
     */
    public StopReason getStopReason() {
        return stopReason;
    }

    /**
     * Imposta il motivo di arresto della partita.
     *
     * @param stopReason motivo di arresto da registrare; può essere {@code null}
     * @see #getStopReason()
     */
    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    /**
     * Restituisce lo stato corrente della scacchiera.
     *
     * @return rappresentazione testuale dello stato della scacchiera; non è {@code null}
     * @see #setBoardState(String)
     * @see #serializeBoardState()
     */
    public String getBoardState() {
        return boardState;
    }

    /**
     * Imposta lo stato della scacchiera con la rappresentazione indicata.
     *
     * @param boardState rappresentazione testuale del nuovo stato della scacchiera; non deve essere {@code null}
     * @see #getBoardState()
     */
    public void setBoardState(String boardState) {
        this.boardState = boardState;
    }

    /**
     * Restituisce l'indice del partecipante il cui turno è attualmente in corso.
     *
     * @return indice del turno corrente; è un valore compreso tra {@code 0} e {@code 1}
     * @see #setTurnIndex(int)
     * @see #endTurn()
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /**
     * Imposta manualmente l'indice del turno di gioco.
     *
     * @param turnIndex indice del turno da impostare; deve essere un valore coerente con il numero di giocatori
     * @see #getTurnIndex()
     */
    public void setTurnIndex(int turnIndex) {
        this.turnIndex = turnIndex;
    }

    /**
     * Avvia la partita con i partecipanti indicati.
     *
     * <p>La partita passa in stato di esecuzione, i partecipanti vengono registrati
     * e il motivo di arresto viene azzerato.</p>
     *
     * @param participants lista dei giocatori che prendono parte alla partita; non deve essere {@code null}
     * @see #stop(StopReason)
     * @see #pause()
     * @see #resume()
     */
    @Override
    public void start(List<UserId> participants) {
        this.running = true;
        this.participants = participants;
        this.stopReason = null;
    }

    /**
     * Arresta la partita registrando il motivo indicato.
     *
     * <p>La partita passa in stato di non esecuzione e il motivo di arresto viene memorizzato.</p>
     *
     * @param reason motivo dell'arresto della partita; non deve essere {@code null}
     * @see #start(List)
     * @see #getStopReason()
     */
    @Override
    public void stop(StopReason reason) {
        this.running = false;
        this.stopReason = reason;
    }

    /**
     * Sospende l'esecuzione della partita senza registrare un motivo di arresto.
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
     * Restituisce il tipo di gioco associato a questa partita.
     *
     * @return {@link GameType#CHESS} per tutte le istanze della classe
     */
    @Override
    public GameType getGameType() {
        return GameType.CHESS;
    }

    /**
     * Restituisce il numero minimo di giocatori richiesto per la partita.
     *
     * @return numero minimo di partecipanti, pari a {@code 2}
     */
    @Override
    public int getMinPlayers() {
        return 2;
    }

    /**
     * Restituisce il numero massimo di giocatori ammessi per la partita.
     *
     * @return numero massimo di partecipanti, pari a {@code 2}
     */
    @Override
    public int getMaxPlayers() {
        return 2;
    }

    /**
     * Passa al turno del giocatore successivo tra i due partecipanti.
     *
     * <p>L'indice del turno viene alternato tra {@code 0} e {@code 1}.</p>
     *
     * @throws IllegalStateException se la partita non è in esecuzione
     * @see #getTurnIndex()
     */
    public void endTurn() {
        if (!this.running) {
            throw new IllegalStateException("ChessGame is not running");
        }
        turnIndex = (turnIndex + 1) % 2;
    }

    /**
     * Restituisce la rappresentazione serializzata dello stato della scacchiera.
     *
     * @return stringa contenente lo stato serializzato della scacchiera; non è {@code null}
     * @see #getBoardState()
     */
    public String serializeBoardState() {
        return boardState;
    }

    /**
     * Restituisce la rappresentazione dello stato iniziale della scacchiera.
     *
     * @return stringa che descrive lo stato di scacchiera prima dell'avvio della partita; non è {@code null}
     */
    private String initialBoard() {
        return "Manco hai iniziato la partita";
    }
}
