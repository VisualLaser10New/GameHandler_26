package com.gameplatform.shared.domain.model;

/**
 * Enumera le condizioni che determinano l'esito di una partita sulla piattaforma di gioco.
 *
 * <p>Ogni costante rappresenta il motivo per cui una partita termina e viene valutata
 * dal gestore del gioco per assegnare il risultato a un singolo giocatore o a una squadra.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameResult
 */
public enum WinCondition {
    /**
     * Indica che un giocatore ha vinto la partita individuale superando l'avversario.
     */
    WIN,

    /**
     * Indica che la partita termina in pareggio, senza un vincitore.
     */
    DRAW,

    /**
     * Indica che la partita viene abbandonata da uno o pi&ugrave; partecipanti prima della conclusione.
     */
    ABANDONED,

    /**
     * Indica che il tempo a disposizione &egrave; scaduto determinando la fine della partita.
     */
    TIMEOUT,

    /**
     * Indica che una squadra ha ottenuto la vittoria in una partita a squadre.
     */
    TEAM_VICTORY
}
