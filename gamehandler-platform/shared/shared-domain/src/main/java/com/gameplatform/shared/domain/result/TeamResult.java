package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * Variante di {@link GameResult} per gli incontri a torneo di squadra.
 * Un incontro di squadra produce un unico vincitore, rappresentato sia
 * dall'identificativo singolo {@code winnerId} sia dall'identificativo di
 * squadra {@code winnerTeamId}; {@code getWinnerIds()} restituisce pertanto
 * un elenco contenente un solo elemento.
 *
 * <p>Si tratta di un record Java puro, privo di annotazioni in ottemperanza
 * alle regole del modulo shared-domain, e viene serializzato tramite il
 * {@code GameResultMixIn} di shared-mqtt sotto il discriminatore
 * {@code "TEAM"}.</p>
 *
 * @see GameResult
 * @see GameResultMixIn
 */
public record TeamResult(
        UserId winnerId,
        List<UserId> winnerIds,
        TeamId winnerTeamId,
        WinCondition winCondition
) implements GameResult {

    /**
     * Costruttore compatto che normalizza i valori dei componenti.
     * Deriva {@code winnerId} da {@code winnerTeamId} quando il primo è
     * {@code null} e {@code winnerTeamId} non lo è, e imposta {@code winnerIds}
     * a un elenco di un solo elemento uguale a {@code winnerId} quando il
     * parametro è {@code null} o vuoto.
     *
     * @param winnerId     identificativo del vincitore singolo; può essere
     *                     {@code null} se viene fornito {@code winnerTeamId}
     * @param winnerIds    elenco degli identificativi dei vincitori; se
     *                     {@code null} o vuoto, viene sostituito con un elenco
     *                     contenente {@code winnerId}
     * @param winnerTeamId identificativo della squadra vincitrice; può essere
     *                     {@code null}, nel qual caso {@code winnerId} resta
     *                     invariato
     * @param winCondition condizione di vittoria associata all'incontro; non
     *                     può essere {@code null}
     */
    public TeamResult {
        if (winnerId == null && winnerTeamId != null) {
            winnerId = new UserId(winnerTeamId.value());
        }
        if (winnerIds == null || winnerIds.isEmpty()) {
            winnerIds = List.of(winnerId);
        }
    }

    /**
     * Restituisce l'identificativo del vincitore dell'incontro.
     *
     * @return l'identificativo del vincitore singolo; non è {@code null}
     *         quando è stato fornito {@code winnerTeamId}
     */
    @Override
    public UserId getWinnerId() {
        return winnerId;
    }

    /**
     * Restituisce l'elenco degli identificativi dei vincitori dell'incontro.
     * Per un incontro di squadra l'elenco contiene un solo elemento, pari a
     * {@code winnerId}.
     *
     * @return l'elenco degli identificativi dei vincitori; non è {@code null}
     *         e contiene esattamente un elemento
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        return winnerIds;
    }

    /**
     * Restituisce la condizione di vittoria che ha determinato l'esito
     * dell'incontro.
     *
     * @return la condizione di vittoria associata al risultato; non è
     *         {@code null}
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}