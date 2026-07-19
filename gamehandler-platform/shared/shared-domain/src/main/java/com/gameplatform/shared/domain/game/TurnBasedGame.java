package com.gameplatform.shared.domain.game;

import com.gameplatform.shared.domain.model.UserId;

/**
 * Contratto per i giochi strutturati in turni alternati tra i partecipanti.
 *
 * <p>Definisce le operazioni essenziali per interrogare e avanzare lo stato
 * di un turno, astraendo la gestione del giocatore attivo e della numerazione
 * delle mani.</p>
 *
 * @see com.gameplatform.shared.domain.model.UserId
 */
public interface TurnBasedGame {

    /**
     * Restituisce il giocatore che deve eseguire la mossa nel turno corrente.
     *
     * @return l'identificativo del giocatore attivo; non è {@code null}
     * @see #endTurn()
     */
    UserId getCurrentPlayer();

    /**
     * Termina il turno del giocatore corrente e passa il controllo al partecipante successivo.
     *
     * <p>Al termine dell'operazione il giocatore attivo diventa quello successivo
     * secondo l'ordine di gioco previsto.</p>
     *
     * @throws IllegalStateException se il gioco è già terminato e non è possibile
     *                               avanzare oltre il turno corrente
     * @see #getCurrentPlayer()
     * @see #getTurnNumber()
     */
    void endTurn();

    /**
     * Restituisce il numero progressivo del turno attualmente in corso.
     *
     * @return il numero del turno corrente; è un intero strettamente positivo
     *         (minimo {@code 1} per il primo turno), mai minore di {@code 0}
     */
    int getTurnNumber();
}
