package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

/**
 * Risultato finale di una partita di Monopoly.
 *
 * <p>Rappresenta in forma immutabile l'esito di un incontro, includendo il vincitore o i vincitori,
 * il patrimonio finale e le proprietà possedute da ciascun giocatore, nonché la condizione di vittoria
 * raggiunta. Implementa {@link GameResult} ed è quindi utilizzabile come esito generico di gioco.</p>
 *
 * @see GameResult
 * @see WinCondition
 * @see UserId
 */
public record MonopolyResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalMoney, Map<String, List<String>> ownedProperties, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del vincitore della partita.
     *
     * <p>In caso di vittoria condivisa tra più giocatori il valore può essere {@code null};
     * in tale situazione occorre fare riferimento a {@link #getWinnerIds()} per ottenere l'elenco completo.</p>
     *
     * @return l'identificativo del vincitore, o {@code null} se la partita prevede più vincitori
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco degli identificativi di tutti i vincitori della partita.
     *
     * <p>Per le partite con un singolo vincitore la lista contiene un solo elemento;
     * in assenza di vincitori restituisce una lista vuota (mai {@code null}).</p>
     *
     * @return la lista degli identificativi dei vincitori, non {@code null}; può essere vuota
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione di vittoria che ha determinato la conclusione della partita.
     *
     * @return la condizione di vittoria raggiunta, non {@code null}
     * @see WinCondition
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
