package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.*;

import java.util.List;

/**
 * Rappresenta il ciclo di vita di una partita gestita dalla piattaforma, definendo le
 * operazioni per avviare, sospendere, riprendere e terminare il gioco nonché per
 * interrogare il suo stato e i suoi attributi.
 *
 * @see GameStatus
 * @see GameType
 * @see GameSessionId
 */
public interface GameLifecycle {

    /**
     * Avvia una nuova partita con gli utenti partecipanti indicati.
     *
     * @param participants la lista degli identificativi degli utenti che prendono parte
     *                     alla partita; non deve essere {@code null} e non deve essere
     *                     vuota, e il suo numero di elementi deve essere compreso
     *                     nell'intervallo definito da {@link #getMinPlayers()} e
     *                     {@link #getMaxPlayers()}
     * @throws IllegalStateException se la partita è già in esecuzione o se il numero di
     *                               partecipanti non rispetta i limiti minimo e massimo
     * @throws NullPointerException se {@code participants} è {@code null}
     * @see #getMinPlayers()
     * @see #getMaxPlayers()
     */
    void start(List<UserId> participants);

    /**
     * Termina la partita in corso per il motivo specificato.
     *
     * @param reason il motivo che giustifica l'arresto della partita; non deve essere
     *               {@code null}
     * @throws IllegalStateException se la partita non è attualmente in esecuzione
     * @throws NullPointerException se {@code reason} è {@code null}
     * @see StopReason
     */
    void stop(StopReason reason);

    /**
     * Sospende temporaneamente la partita in corso, consentendone la ripresa successiva
     * senza perdere lo stato del gioco.
     *
     * @throws IllegalStateException se la partita non è in esecuzione oppure è già sospesa
     * @see #resume()
     */
    void pause();

    /**
     * Riprende una partita precedentemente sospesa, riportandola allo stato di esecuzione.
     *
     * @throws IllegalStateException se la partita non è sospesa o non è mai stata avviata
     * @see #pause()
     */
    void resume();

    /**
     * Restituisce lo stato corrente della partita.
     *
     * @return lo stato della partita, mai {@code null}
     * @see GameStatus
     */
    GameStatus getStatus();

    /**
     * Restituisce il tipo di gioco associato a questa partita.
     *
     * @return il tipo di gioco, mai {@code null}
     * @see GameType
     */
    GameType getGameType();

    /**
     * Restituisce l'identificativo univoco della sessione di gioco.
     *
     * @return l'identificativo della sessione, mai {@code null}
     * @see GameSessionId
     */
    GameSessionId getSessionId();

    /**
     * Restituisce la lista degli utenti attualmente partecipanti alla partita.
     *
     * @return la lista degli identificativi dei partecipanti, mai {@code null}; può
     *         essere vuota se la partita non è ancora stata avviata
     */
    List<UserId> getParticipants();

    /**
     * Restituisce il numero minimo di giocatori richiesto per avviare la partita.
     *
     * @return il numero minimo di giocatori, sempre maggiore o uguale a {@code 1}
     */
    int getMinPlayers();

    /**
     * Restituisce il numero massimo di giocatori ammessi nella partita.
     *
     * @return il numero massimo di giocatori, sempre maggiore o uguale a
     *         {@link #getMinPlayers()}
     */
    int getMaxPlayers();
}
