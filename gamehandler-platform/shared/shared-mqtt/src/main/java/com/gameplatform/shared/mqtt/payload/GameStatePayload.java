package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.GameMachineStatus;

/**
 * Payload utilizzato per trasportare, tramite il broker MQTT, lo stato corrente
 * di una macchina da gioco associato a un determinato gioco e utente.
 *
 * <p>Incapsula l'identificativo del gioco, lo stato della macchina e
 * l'identificativo dell'utente coinvolto, rappresentando il messaggio scambiato
 * tra i componenti della piattaforma per la sincronizzazione dello stato.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameMachineStatus
 */
public record GameStatePayload(
    String gameId,
    GameMachineStatus status,
    String userId
) {}
