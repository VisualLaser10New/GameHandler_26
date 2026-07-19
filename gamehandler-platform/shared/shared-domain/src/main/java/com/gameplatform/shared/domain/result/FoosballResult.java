package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

/**
 * Rappresenta il risultato di una partita di calcio balilla all'interno della piattaforma.
 *
 * <p>Incapsula le informazioni essenziali sull'esito dell'incontro, ovvero il vincitore singolo,
 * l'elenco dei vincitori in caso di vittoria condivisa, i punteggi finali e la condizione
 * di vittoria applicata. Implementa {@link GameResult} ed è definito come record immutabile.</p>
 *
 * @see GameResult
 * @see UserId
 * @see WinCondition
 */
public record FoosballResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalScores, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del vincitore della partita.
     *
     * <p>In caso di vittoria condivisa tra più giocatori, il valore restituito può essere {@code null}
     * a indicare l'assenza di un vincitore singolo; in tale situazione occorre fare riferimento a
     * {@link #getWinnerIds()}.</p>
     *
     * @return l'identificativo del vincitore singolo, o {@code null} se la vittoria è condivisa
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco degli identificativi dei vincitori della partita.
     *
     * <p>L'elenco è vuoto quando la partita prevede un unico vincitore, rappresentato da
     * {@link #getWinnerId()}. Non restituisce mai {@code null}: in assenza di vincitori ritorna
     * una lista vuota.</p>
     *
     * @return la lista degli identificativi dei vincitori, non {@code null}; vuota se non applicabile
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione di vittoria che ha determinato l'esito della partita.
     *
     * <p>Descrive il criterio utilizzato per stabilire il vincitore, ad esempio il raggiungimento
     * di un punteggio soglia. Il valore non è {@code null}.</p>
     *
     * @return la condizione di vittoria applicata, non {@code null}
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
