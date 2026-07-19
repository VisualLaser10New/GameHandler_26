package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;
import java.util.Map;

/**
 * Rappresenta il risultato finale di una partita di Risk.
 *
 * <p>Contiene le informazioni essenziali sull'esito del gioco, ovvero il vincitore singolo,
 * l'elenco dei vincitori in caso di vittoria condivisa, il controllo dei territori al termine
 * della partita, il numero totale di turni disputati e la condizione di vittoria soddisfatta.</p>
 *
 * @see GameResult
 * @see WinCondition
 * @see UserId
 */
public record RiskResult(UserId winnerId, List<UserId> winnerIds, Map<String, Map<String, Integer>> territoriesAtEnd, int totalRounds, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del giocatore vincitore della partita.
     *
     * <p>In caso di vittoria condivisa o di assenza di un vincitore unico, il valore
     * restituito può essere {@code null}.</p>
     *
     * @return l'identificativo del vincitore singolo, o {@code null} se non presente
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco dei giocatori risultati vincitori della partita.
     *
     * <p>L'elenco è vuoto qualora la partita non preveda vincitori oppure preveda
     * un unico vincitore rappresentato tramite {@link #getWinnerId()}. Non è mai
     * {@code null}.</p>
     *
     * @return la lista degli identificativi dei vincitori, eventualmente vuota ma mai {@code null}
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione di vittoria che è stata soddisfatta per concludere la partita.
     *
     * @return la condizione di vittoria raggiunta, non {@code null}
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
