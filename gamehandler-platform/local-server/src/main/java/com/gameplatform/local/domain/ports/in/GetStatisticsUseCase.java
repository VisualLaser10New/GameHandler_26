package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.shared.domain.model.GameType;
import java.util.List;

/**
 * Use case per la lettura delle statistiche generali di gioco.
 * Fornisce le metriche aggregate per tipo di gioco e l'elenco
 * delle sessioni di gioco attualmente in corso.
 *
 * @see com.gameplatform.local.domain.model.LocalStatistics
 */
public interface GetStatisticsUseCase {
    /**
     * Restituisce le statistiche aggregate per il tipo di gioco specificato.
     *
     * @param gameType tipo di gioco per cui calcolare le statistiche
     * @return le statistiche locali calcolate
     */
    LocalStatistics getStatistics(GameType gameType);

    /**
     * Restituisce l'elenco delle sessioni di gioco attualmente attive.
     *
     * @return lista delle sessioni di gioco attive
     */
    List<GameSession> getActiveSessions();
}
