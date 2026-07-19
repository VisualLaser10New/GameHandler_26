package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * Rappresenta il risultato finale di una partita di scacchi, inclusi il o i vincitori,
 * la condizione di vittoria, il motivo di terminazione e lo stato finale della scacchiera.
 *
 * <p>Implementa {@link GameResult} per fornire una descrizione completa e immutabile
 * dell'esito di un'incontro scacchistico.</p>
 *
 * @see GameResult
 * @see WinCondition
 * @see UserId
 */
public record ChessResult(UserId winnerId, List<UserId> winnerIds, String terminationReason, String finalFenState, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del vincitore della partita.
     *
     * <p>In caso di pareggio o di assenza di un vincitore unico, il valore restituito
     * è {@code null}.</p>
     *
     * @return l'identificativo del vincitore, oppure {@code null} se la partita
     *         non ha un vincitore unico
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco degli identificativi dei vincitori della partita.
     *
     * <p>L'elenco è vuoto se la partita non prevede vincitori (ad esempio in caso di
     * annullamento). Non è {@code null}.</p>
     *
     * @return la lista degli identificativi dei vincitori, mai {@code null}; può
     *         essere vuota
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione che ha determinato la vittoria nella partita.
     *
     * <p>Il valore può essere {@code null} se la condizione di vittoria non è
     * stata classificata.</p>
     *
     * @return la condizione di vittoria, oppure {@code null} se non disponibile
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
