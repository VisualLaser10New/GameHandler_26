package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.HeartbeatPayload;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Pubblica messaggi heartbeat periodici sul broker MQTT.
 * <p>
 * Ogni heartbeat contiene l'identificativo della macchina da gioco e il
 * timestamp corrente. I messaggi vengono inviati sul topic
 * {@code building/{buildingId}/game/{gameId}/heartbeat} con QoS 0
 * (fire-and-forget) e senza retention.
 */
public class HeartbeatPublisher {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Costruisce un publisher di heartbeat per l'adapter e l'edificio specificati.
     *
     * @param adapter    l'adapter MQTT utilizzato per la pubblicazione
     * @param buildingId l'identificativo dell'edificio per la costruzione del topic
     */
    public HeartbeatPublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Pubblica un heartbeat per la macchina da gioco specificata.
     *
     * @param gameId l'identificativo della macchina da gioco
     */
    public void publishHeartbeat(String gameId) {
        try {
            String topic = MqttTopics.heartbeat(buildingId, gameId);
            HeartbeatPayload payload = new HeartbeatPayload(gameId, Instant.now());
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.debug("Publishing heartbeat to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.HEARTBEAT, false);
        } catch (MqttException e) {
            log.error("Failed to publish heartbeat for game {}: {}", gameId, e.getMessage(), e);
        }
    }
}
