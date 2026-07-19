package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import java.util.List;

/**
 * Use case per la lettura dei giochi associati a una struttura.
 * Restituisce l'elenco dei gioci disponibili presso la struttura
 * specificata.
 *
 * @see com.gameplatform.local.domain.model.Game
 */
public interface ListBuildingGamesUseCase {
    /**
     * Restituisce l'elenco dei giochi per la struttura specificata.
     *
     * @param buildingId identificativo della struttura
     * @return lista dei giochi della struttura
     */
    List<Game> getByBuilding(BuildingId buildingId);
}