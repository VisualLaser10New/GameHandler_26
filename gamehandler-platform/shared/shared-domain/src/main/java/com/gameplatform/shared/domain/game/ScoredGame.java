package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.UserId;

import java.util.Map;

/**
 * Rappresenta un gioco che tiene traccia del punteggio dei giocatori partecipanti.
 *
 * <p>Espone le operazioni per consultare i punteggi correnti e per registrarne
 * la variazione nel corso della partita.</p>
 *
 * @see UserId
 */
public interface ScoredGame {

    /**
     * Restituisce i punteggi correnti di tutti i giocatori partecipanti al gioco.
     *
     * @return una mappa immutabile che associa a ciascun {@link UserId} il proprio
     *         punteggio intero; la mappa è vuota se nessun giocatore ha ancora
     *         registrato un punteggio
     */
    Map<UserId, Integer> getCurrentScores();

    /**
     * Registra la variazione di punteggio di un giocatore a seguito di un evento
     * di gioco.
     *
     * <p>Applica l'incremento o il decremento {@code delta} al punteggio attuale
     * del giocatore. Un valore {@code delta} pari a {@code 0} non modifica il
     * punteggio. Un {@code delta} negativo riduce il punteggio; il comportamento
     * in caso di superamento di un limite inferiore è definito dall'implementazione.</p>
     *
     * @param player il giocatore di cui aggiornare il punteggio; non deve essere
     *               {@code null}
     * @param delta  la variazione di punteggio da applicare, positiva, negativa o
     *               nulla
     * @throws NullPointerException se {@code player} è {@code null}
     * @see #getCurrentScores()
     */
    void recordScore(UserId player, int delta);
}
