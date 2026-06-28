package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.GameStatePayload;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes game machine state changes to the MQTT broker.
 * <p>
 * Uses {@link MqttClientAdapter} to send {@link GameStatePayload} messages
 * on the topic {@code building/{buildingId}/game/{gameId}/state}
 * with QoS 1 and the retained flag set.
 */
public class GameStatePublisher {

    private static final Logger log = LoggerFactory.getLogger(GameStatePublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Creates a publisher for the given adapter and building.
     *
     * @param adapter    the MQTT client adapter used for publishing
     * @param buildingId the building identifier for topic construction
     */
    public GameStatePublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Publishes a state change without associating a user.
     *
     * @param gameId the game machine identifier
     * @param status the new machine status
     */
    public void publishState(String gameId, GameMachineStatus status) {
        publishState(gameId, status, null);
    }

    /**
     * Publishes a state change optionally associated with a user.
     *
     * @param gameId the game machine identifier
     * @param status the new machine status
     * @param userId the user who triggered the change, or {@code null}
     */
    public void publishState(String gameId, GameMachineStatus status, String userId) {
        try {
            String topic = MqttTopics.gameState(buildingId, gameId);
            GameStatePayload payload = new GameStatePayload(gameId, status, userId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing game state to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.STATE, true);
        } catch (MqttException e) {
            log.error("Failed to publish game state for game {}: {}", gameId, e.getMessage(), e);
        }
    }
}
