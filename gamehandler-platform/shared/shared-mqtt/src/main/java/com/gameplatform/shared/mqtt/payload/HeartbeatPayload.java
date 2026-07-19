package com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

/**
 * Payload utilizzato per trasmettere il segnale di heartbeat di un gioco sulla rete MQTT.
 *
 * <p>Incapsula l'identificativo del gioco e l'istante di generazione del segnale,
 * permettendo ai componenti del platform di rilevare la presenza e l'attività delle istanze.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.MqttPayload
 */
public record HeartbeatPayload(
    /**
     * Restituisce l'identificativo del gioco associato al segnale di heartbeat.
     *
     * @return l'identificativo del gioco; può essere {@code null} se non valorizzato
     */
    String gameId,

    /**
     * Restituisce l'istante di generazione del segnale di heartbeat.
     *
     * @return l'istante del segnale; può essere {@code null} se non valorizzato
     */
    Instant timestamp
) {}
