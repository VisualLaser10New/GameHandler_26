package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * Risultato di una sessione di gioco slot che aggrega le statistiche della partita
 * e la condizione di vittoria del giocatore.
 *
 * <p>Incapsula i dati relativi a spin effettuati, crediti investiti e restituiti,
 * la vincita massima ottenuta e la condizione di vittoria associata al visitatore.</p>
 *
 * @see GameResult
 * @see WinCondition
 */
public record SlotResult(String visitorId, int totalSpins, int creditsIn, int creditsOut, int biggestWin, WinCondition winCondition) implements GameResult{

    /**
     * Restituisce l'identificativo del vincitore della sessione slot.
     *
     * <p>Ritorna un {@link UserId} costruito a partire dal {@code visitorId} se la condizione
     * di vittoria è {@link WinCondition#WIN}; in caso contrario ritorna {@code null}.</p>
     *
     * @return l'identificativo del vincitore se la sessione è in stato di vittoria,
     *         {@code null} se il giocatore non ha vinto
     * @see WinCondition#WIN
     */
    @Override
    public UserId getWinnerId() {
        return winCondition == WinCondition.WIN ? new UserId(visitorId) : null;
    }

    /**
     * Restituisce la lista degli identificativi dei vincitori della sessione slot.
     *
     * <p>Se la sessione è in stato di vittoria, ritorna una lista contenente un singolo
     * {@link UserId}; in caso contrario ritorna una lista vuota (non {@code null}).</p>
     *
     * @return una lista non {@code null} contenente l'identificativo del vincitore
     *         se la sessione è vinta, altrimenti una lista vuota
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        UserId winner = getWinnerId();
        return winner != null ? List.of(winner) : List.of();
    }

    /**
     * Restituisce la condizione di vittoria della sessione slot.
     *
     * @return la {@link WinCondition} associata al risultato, non {@code null}
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
