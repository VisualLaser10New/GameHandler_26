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
 * Publishes periodic heartbeat messages to the MQTT broker.
 * <p>
 * Each heartbeat contains the game machine identifier and the current
 * timestamp. Messages are sent on the topic
 * {@code building/{buildingId}/game/{gameId}/heartbeat} with QoS 0
 * (fire-and-forget) and no retention.
 */
public class HeartbeatPublisher {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Creates a heartbeat publisher for the given adapter and building.
     *
     * @param adapter    the MQTT client adapter used for publishing
     * @param buildingId the building identifier for topic construction
     */
    public HeartbeatPublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Publishes a heartbeat for the specified game machine.
     *
     * @param gameId the game machine identifier
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
