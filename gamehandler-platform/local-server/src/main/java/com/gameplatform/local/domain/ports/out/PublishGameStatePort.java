package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;

/**
 * Porta outbound per la pubblicazione dello stato delle macchine da gioco e
 * degli eventi di sessione.
 * <p>
 * Consente di notificare ai sistemi interessati (via MQTT o altri canali)
 * i cambiamenti di stato delle macchine da gioco e gli eventi generati
 * durante le sessioni di gioco.
 * </p>
 */
public interface PublishGameStatePort {
    /**
     * Pubblica il cambiamento di stato di una macchina da gioco.
     *
     * @param gameId l'identificativo della macchina da gioco
     * @param status il nuovo stato operativo della macchina
     */
    void publishState(GameId gameId, GameMachineStatus status);

    /**
     * Pubblica un evento di sessione su un topic specifico.
     *
     * @param topic   il topic del messaggio
     * @param payload il payload dell'evento da pubblicare
     */
    void publishSessionEvent(String topic, Object payload);
}
