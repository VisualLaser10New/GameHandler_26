package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;

/**
 * Use case per la gestione del catalogo giochi locale. Fornisce le
 * operazioni di creazione, aggiornamento ed eliminazione dei giochi
 * nel catalogo della struttura.
 *
 * @see com.gameplatform.local.domain.model.Game
 */
public interface ManageGameCatalogUseCase {
    /**
     * Crea un nuovo gioco nel catalogo con le specifiche fornite.
     *
     * @param gameType   tipo di gioco
     * @param name       nome del gioco
     * @param buildingId identificativo della struttura di appartenenza
     * @return il gioco creato
     */
    Game createGame(GameType gameType, String name, BuildingId buildingId);

    /**
     * Aggiorna le informazioni di un gioco esistente nel catalogo.
     *
     * @param gameId   identificativo del gioco da aggiornare
     * @param newName  nuovo nome del gioco
     * @param newStatus nuovo stato della macchina di gioco
     * @return il gioco aggiornato
     */
    Game updateGame(GameId gameId, String newName, GameMachineStatus newStatus);

    /**
     * Elimina un gioco dal catalogo.
     *
     * @param gameId identificativo del gioco da eliminare
     */
    void deleteGame(GameId gameId);
}