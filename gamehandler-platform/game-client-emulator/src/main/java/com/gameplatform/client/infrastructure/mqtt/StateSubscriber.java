package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.mqtt.MqttTopics;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Subscribes to MQTT topics for game state, session events, heartbeats,
 * and heartbeat acknowledgements.
 * <p>
 * Incoming messages are forwarded to a {@link BiConsumer} callback
 * that receives the topic and raw payload bytes. Supports wildcard
 * subscriptions for all games of a building, as well as specific
 * game-scoped subscriptions.
 */
public class StateSubscriber {

    private static final Logger log = LoggerFactory.getLogger(StateSubscriber.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;
    private final BiConsumer<String, byte[]> messageHandler;

    /**
     * Creates a subscriber for the given adapter, building, and handler.
     *
     * @param adapter        the MQTT client adapter used for subscribing
     * @param buildingId     the building identifier for topic construction
     * @param messageHandler callback that receives (topic, payload) for each message
     */
    public StateSubscriber(MqttClientAdapter adapter, String buildingId,
                           BiConsumer<String, byte[]> messageHandler) {
        this.adapter = adapter;
        this.buildingId = buildingId;
        this.messageHandler = messageHandler;
    }

    /**
     * Subscribes to state topics for all games in the building.
     * Topic pattern: {@code building/{buildingId}/game/+/state}
     */
    public void subscribeToStates() {
        subscribeToStates(null);
    }

    /**
     * Subscribes to state topics, optionally for a specific game.
     *
     * @param specificGameId if non-blank, subscribes to
     *                       {@code building/{id}/game/{gameId}/state};
     *                       otherwise subscribes to the wildcard pattern
     *                       {@code building/{id}/game/+/state}
     */
    public void subscribeToStates(String specificGameId) {
        try {
            String topicFilter;
            if (specificGameId != null && !specificGameId.isBlank()) {
                topicFilter = MqttTopics.gameState(buildingId, specificGameId);
            } else {
                topicFilter = "building/" + buildingId + "/game/+/state";
            }

            log.info("Subscribing to state topic: {}", topicFilter);
            adapter.subscribe(topicFilter, 1, (topic, message) -> {
                log.debug("Received state message on topic {}: {}", topic, new String(message.getPayload()));
                if (messageHandler != null) {
                    messageHandler.accept(topic, message.getPayload());
                }
            });
        } catch (MqttException e) {
            log.error("Failed to subscribe to state topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribes to session event topics for all games in the building.
     * Topic pattern: {@code building/{buildingId}/game/+/session/+}
     */
    public void subscribeToSessionEvents() {
        subscribeToSessionEvents(null);
    }

    /**
     * Subscribes to session event topics, optionally for a specific game.
     *
     * @param specificGameId if non-blank, subscribes to
     *                       {@code building/{id}/game/{gameId}/session/+};
     *                       otherwise subscribes to the wildcard pattern
     *                       {@code building/{id}/game/+/session/+}
     */
    public void subscribeToSessionEvents(String specificGameId) {
        try {
            String topicFilter;
            if (specificGameId != null && !specificGameId.isBlank()) {
                topicFilter = "building/" + buildingId + "/game/" + specificGameId + "/session/+";
            } else {
                topicFilter = "building/" + buildingId + "/game/+/session/+";
            }

            log.info("Subscribing to session event topic: {}", topicFilter);
            adapter.subscribe(topicFilter, 1, (topic, message) -> {
                log.debug("Received session event on topic {}: {}", topic, new String(message.getPayload()));
                if (messageHandler != null) {
                    messageHandler.accept(topic, message.getPayload());
                }
            });
        } catch (MqttException e) {
            log.error("Failed to subscribe to session event topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribes to heartbeat topics for all games in the building.
     * Topic pattern: {@code building/{buildingId}/game/+/heartbeat}
     * QoS 0 (fire-and-forget).
     */
    public void subscribeToHeartbeats() {
        try {
            String topicFilter = "building/" + buildingId + "/game/+/heartbeat";
            log.info("Subscribing to heartbeat topic: {}", topicFilter);
            adapter.subscribe(topicFilter, 0, (topic, message) -> {
                log.debug("Received heartbeat on topic {}: {}", topic, new String(message.getPayload()));
                if (messageHandler != null) {
                    messageHandler.accept(topic, message.getPayload());
                }
            });
        } catch (MqttException e) {
            log.error("Failed to subscribe to heartbeat topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribes to lobby event topics for a specific game.
     * Topic pattern: {@code building/{buildingId}/game/{gameId}/session/lobby/+}
     *
     * @param gameId the game machine identifier to filter on
     */
    public void subscribeToLobbyEvents(String gameId) {
        try {
            String topicFilter = "building/" + buildingId + "/game/" + gameId + "/session/lobby/+";
            log.info("Subscribing to lobby events topic: {}", topicFilter);
            adapter.subscribe(topicFilter, 1, (topic, message) -> {
                log.debug("Received lobby event on topic {}: {}", topic, new String(message.getPayload()));
                if (messageHandler != null) {
                    messageHandler.accept(topic, message.getPayload());
                }
            });
        } catch (MqttException e) {
            log.error("Failed to subscribe to lobby event topics: {}", e.getMessage(), e);
        }
    }

    /**
     * Subscribes to heartbeat acknowledgement topics for all games.
     * Topic pattern: {@code building/{buildingId}/game/+/heartbeat/ack}
     * QoS 0 (fire-and-forget).
     */
    public void subscribeToHeartbeatAcks() {
        try {
            String topicFilter = "building/" + buildingId + "/game/+/heartbeat/ack";
            log.info("Subscribing to heartbeat ack topic: {}", topicFilter);
            adapter.subscribe(topicFilter, 0, (topic, message) -> {
                log.debug("Received heartbeat ack on topic {}: {}", topic, new String(message.getPayload()));
                if (messageHandler != null) {
                    messageHandler.accept(topic, message.getPayload());
                }
            });
        } catch (MqttException e) {
            log.error("Failed to subscribe to heartbeat ack topics: {}", e.getMessage(), e);
        }
    }
}
