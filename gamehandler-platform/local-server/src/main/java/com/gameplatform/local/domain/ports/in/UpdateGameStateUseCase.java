package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;

/**
 * Use case per l'aggiornamento dello stato di una macchina da gioco.
 * Modifica lo stato operativo di un gioco nel catalogo, consentendo
 * di abilitarlo, disabilitarlo o impostarne la manutenzione.
 */
public interface UpdateGameStateUseCase {
    /**
     * Aggiorna lo stato della macchina da gioco specificata.
     *
     * @param gameId    identificativo del gioco
     * @param newStatus nuovo stato della macchina da gioco
     */
    void updateState(GameId gameId, GameMachineStatus newStatus);
}
