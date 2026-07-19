package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;

/**
 * Use case per la lettura delle statistiche di una struttura.
 * Calcola e restituisce le metriche di utilizzo per un tipo di
 * gioco specifico all'interno di una determinata struttura.
 *
 * @see com.gameplatform.local.domain.model.LocalStatistics
 */
public interface GetBuildingStatisticsUseCase {
    /**
     * Restituisce le statistiche per il tipo di gioco e la struttura specificati.
     *
     * @param gameType   tipo di gioco per cui calcolare le statistiche
     * @param buildingId identificativo della struttura
     * @return le statistiche locali calcolate
     */
    LocalStatistics getStatisticsForBuilding(GameType gameType, BuildingId buildingId);
}