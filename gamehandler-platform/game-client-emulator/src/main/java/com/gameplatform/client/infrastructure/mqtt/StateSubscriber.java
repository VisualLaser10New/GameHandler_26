package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.mqtt.MqttTopics;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Sottoscrive topic MQTT per stato di gioco, eventi di sessione, heartbeat
 * e acknowledgement degli heartbeat.
 * <p>
 * I messaggi in arrivo vengono inoltrati a un callback {@link BiConsumer}
 * che riceve il topic e i byte del payload. Supporta sottoscrizioni con
 * wildcard per tutti i giochi di un edificio, nonché sottoscrizioni
 * specifiche per un singolo gioco.
 */
public class StateSubscriber {

    private static final Logger log = LoggerFactory.getLogger(StateSubscriber.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;
    private final BiConsumer<String, byte[]> messageHandler;

    /**
     * Costruisce un subscriber per l'adapter, l'edificio e l'handler specificati.
     *
     * @param adapter        l'adapter MQTT utilizzato per la sottoscrizione
     * @param buildingId     l'identificativo dell'edificio per la costruzione del topic
     * @param messageHandler callback che riceve (topic, payload) per ogni messaggio
     */
    public StateSubscriber(MqttClientAdapter adapter, String buildingId,
                           BiConsumer<String, byte[]> messageHandler) {
        this.adapter = adapter;
        this.buildingId = buildingId;
        this.messageHandler = messageHandler;
    }

    /**
     * Sottoscrive i topic di stato per tutti i giochi dell'edificio.
     * Pattern del topic: {@code building/{buildingId}/game/+/state}
     *
     * @see #subscribeToStates(String)
     */
    public void subscribeToStates() {
        subscribeToStates(null);
    }

    /**
     * Sottoscrive i topic di stato, opzionalmente per un gioco specifico.
     *
     * @param specificGameId se non blank, sottoscrive
     *                       {@code building/{id}/game/{gameId}/state};
     *                       altrimenti sottoscrive il pattern con wildcard
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
     * Sottoscrive i topic degli eventi di sessione per tutti i giochi dell'edificio.
     * Pattern del topic: {@code building/{buildingId}/game/+/session/+}
     *
     * @see #subscribeToSessionEvents(String)
     */
    public void subscribeToSessionEvents() {
        subscribeToSessionEvents(null);
    }

    /**
     * Sottoscrive i topic degli eventi di sessione, opzionalmente per un gioco specifico.
     *
     * @param specificGameId se non blank, sottoscrive
     *                       {@code building/{id}/game/{gameId}/session/+};
     *                       altrimenti sottoscrive il pattern con wildcard
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
     * Sottoscrive i topic heartbeat per tutti i giochi dell'edificio.
     * Pattern del topic: {@code building/{buildingId}/game/+/heartbeat}
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
     * Sottoscrive i topic degli eventi di lobby per un gioco specifico.
     * Pattern del topic: {@code building/{buildingId}/game/{gameId}/session/lobby/+}
     *
     * @param gameId l'identificativo della macchina da gioco su cui filtrare
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
     * Sottoscrive i topic di acknowledgement heartbeat per tutti i giochi.
     * Pattern del topic: {@code building/{buildingId}/game/+/heartbeat/ack}
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
