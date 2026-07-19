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
 * Pubblica le variazioni di stato delle macchine da gioco sul broker MQTT.
 * <p>
 * Utilizza {@link MqttClientAdapter} per inviare messaggi {@link GameStatePayload}
 * sul topic {@code building/{buildingId}/game/{gameId}/state} con QoS 1
 * e il flag retained attivo.
 */
public class GameStatePublisher {

    private static final Logger log = LoggerFactory.getLogger(GameStatePublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Costruisce un publisher per l'adapter e l'edificio specificati.
     *
     * @param adapter    l'adapter MQTT utilizzato per la pubblicazione
     * @param buildingId l'identificativo dell'edificio per la costruzione del topic
     */
    public GameStatePublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Pubblica una variazione di stato senza associare un utente.
     *
     * @param gameId l'identificativo della macchina da gioco
     * @param status il nuovo stato della macchina
     * @see #publishState(String, GameMachineStatus, String)
     */
    public void publishState(String gameId, GameMachineStatus status) {
        publishState(gameId, status, null);
    }

    /**
     * Pubblica una variazione di stato associata opzionalmente a un utente.
     *
     * @param gameId l'identificativo della macchina da gioco
     * @param status il nuovo stato della macchina
     * @param userId l'utente che ha provocato la variazione, oppure {@code null}
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
