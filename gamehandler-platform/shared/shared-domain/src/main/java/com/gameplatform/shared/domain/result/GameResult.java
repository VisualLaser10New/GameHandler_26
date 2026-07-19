package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * Rappresenta il risultato finale di una partita, esponendo le informazioni
 * relative al vincitore o ai vincitori e alla condizione di vittoria raggiunta.
 *
 * @see com.gameplatform.shared.domain.model.UserId
 * @see com.gameplatform.shared.domain.model.WinCondition
 */
public interface GameResult {

    /**
     * Restituisce l'identificativo del vincitore della partita.
     *
     * @return l'identificativo del vincitore, oppure {@code null} se la partita
     *         non ha un vincitore unico (ad esempio in caso di pareggio o di
     *         vittoria condivisa)
     */
    UserId getWinnerId();

    /**
     * Restituisce l'elenco degli identificativi di tutti i vincitori della partita.
     *
     * @return la lista dei vincitori; la lista è vuota se la partita non ha alcun
     *         vincitore, mai {@code null}
     */
    List<UserId> getWinnerIds();

    /**
     * Restituisce la condizione di vittoria che ha determinato l'esito della partita.
     *
     * @return la condizione di vittoria raggiunta, mai {@code null}
     */
    WinCondition getWinCondition();
}
