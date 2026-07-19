package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

/**
 * Risultato di una partita di freccette.
 *
 * <p>Rappresenta l'esito finale di una sessione di gioco includendo il vincitore singolo,
 * l'eventuale elenco di vincitori in caso di pareggio, i punteggi e i tiri finali di ciascun
 * giocatore e la condizione di vittoria applicata.</p>
 *
 * @see GameResult
 * @see WinCondition
 */
public record DartsResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalScores, Map<String, Integer> dartsThrown, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del giocatore risultato vincitore della partita.
     *
     * <p>In caso di pareggio tra pi&ugrave; giocatori il valore pu&ograve; essere {@code null}
     * a favore dell'elenco restituito da {@link #getWinnerIds()}.</p>
     *
     * @return l'identificativo del vincitore singolo, oppure {@code null} se la partita
     *         termina in pareggio o non &egrave; presente un vincitore univoco
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco degli identificativi dei giocatori risultati vincitori della partita.
     *
     * <p>L'elenco include tutti i giocatori a pari merito nel caso di pareggio. Pu&ograve; essere
     * vuoto se nessun vincitore &egrave; stato determinato, e i suoi elementi non sono mai
     * {@code null}.</p>
     *
     * @return la lista dei vincitori, mai {@code null}; pu&ograve; essere vuota o contenere
     *         pi&ugrave; elementi in caso di pareggio
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione di vittoria che &egrave; stata applicata per determinare l'esito della partita.
     *
     * @return la condizione di vittoria applicata, mai {@code null}
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
