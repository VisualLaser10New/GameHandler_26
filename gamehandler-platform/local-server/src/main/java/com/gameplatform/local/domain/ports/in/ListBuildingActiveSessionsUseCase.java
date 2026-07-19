package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.BuildingId;
import java.util.List;

/**
 * Use case per la lettura delle sessioni di gioco attive in una struttura.
 * Restituisce l'elenco di tutte le sessioni attualmente in corso
 * all'interno della struttura specificata.
 *
 * @see com.gameplatform.local.domain.model.GameSession
 */
public interface ListBuildingActiveSessionsUseCase {
    /**
     * Restituisce le sessioni di gioco attive per la struttura specificata.
     *
     * @param buildingId identificativo della struttura
     * @return lista delle sessioni di gioco attive nella struttura
     */
    List<GameSession> getActiveSessionsByBuilding(BuildingId buildingId);
}