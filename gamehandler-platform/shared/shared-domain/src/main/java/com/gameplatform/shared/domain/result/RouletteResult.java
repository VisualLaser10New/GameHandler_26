package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * Risultato di una partita di roulette che riporta i dettagli del giocatore,
 * le statistiche complessive della sessione e la condizione di vincita.
 *
 * @see GameResult
 * @see WinCondition
 */
public record RouletteResult(String visitorId, int totalRounds, int totalBetAmount, int totalPayout, List<String> winningNumbers, WinCondition winCondition) implements GameResult {

    /**
     * Restituisce l'identificativo del giocatore vincente.
     *
     * <p>Se la condizione di vincita è {@link WinCondition#WIN}, ritorna un nuovo
     * {@link UserId} costruito a partire dal {@code visitorId}; in tutti gli altri
     * casi ritorna {@code null}.</p>
     *
     * @return l'identificativo del vincitore se la partita è vinta, {@code null} altrimenti
     * @see WinCondition
     * @see #getWinnerIds()
     */
    @Override
    public UserId getWinnerId() {
        return winCondition == WinCondition.WIN ? new UserId(visitorId) : null;
    }

    /**
     * Restituisce l'elenco degli identificativi dei giocatori vincenti.
     *
     * <p>Se la partita è vinta, ritorna una lista contenente un unico
     * {@link UserId} corrispondente al vincitore; in caso contrario ritorna
     * una lista vuota (non {@code null}).</p>
     *
     * @return lista non {@code null} contenente il vincitore se presente, altrimenti una lista vuota
     * @see #getWinnerId()
     */
    @Override
    public List<UserId> getWinnerIds() {
        UserId winner = getWinnerId();
        return winner != null ? List.of(winner) : List.of();
    }

    /**
     * Restituisce la condizione di vincita della partita.
     *
     * @return la {@link WinCondition} associata al risultato
     * @see WinCondition
     */
    @Override
    public WinCondition getWinCondition() {
        return winCondition;
    }
}
