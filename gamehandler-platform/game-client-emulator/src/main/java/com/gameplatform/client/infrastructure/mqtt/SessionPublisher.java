package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.SessionEndPayload;
import com.gameplatform.shared.mqtt.payload.SessionPausePayload;
import com.gameplatform.shared.mqtt.payload.SessionResumePayload;
import com.gameplatform.shared.mqtt.payload.SessionStartPayload;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Publishes game session lifecycle events to the MQTT broker.
 * <p>
 * Supports four session event types: start, end, pause, and resume.
 * Each method constructs the appropriate payload and topic using
 * {@link MqttTopics} helper methods and publishes with QoS 1
 * (non-retained) via the underlying {@link MqttClientAdapter}.
 */
public class SessionPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionPublisher.class);

    private final MqttClientAdapter adapter;
    private final String buildingId;

    /**
     * Creates a session publisher for the given adapter and building.
     *
     * @param adapter    the MQTT client adapter used for publishing
     * @param buildingId the building identifier for topic construction
     */
    public SessionPublisher(MqttClientAdapter adapter, String buildingId) {
        this.adapter = adapter;
        this.buildingId = buildingId;
    }

    /**
     * Publishes a session start event.
     *
     * @param gameId       the game machine identifier
     * @param sessionId    the session identifier
     * @param gameType     the type of game being played
     * @param participants the list of participant user IDs
     */
    public void publishStart(String gameId, String sessionId, GameType gameType, List<String> participants) {
        try {
            String topic = MqttTopics.sessionStart(buildingId, gameId);
            SessionStartPayload payload = new SessionStartPayload(sessionId, gameType, participants);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session start to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session start", e);
        }
    }

    /**
     * Publishes a session end event with results.
     *
     * @param gameId      the game machine identifier
     * @param sessionId   the session identifier
     * @param winnerId    the winner's user ID, or {@code null} for draws
     * @param winCondition how the session ended (e.g. WIN, DRAW, TIMEOUT)
     * @param resultData  optional JSON string with detailed results
     */
    public void publishEnd(String gameId, String sessionId, String winnerId,
                           WinCondition winCondition, String resultData) {
        try {
            String topic = MqttTopics.sessionEnd(buildingId, gameId);
            SessionEndPayload payload = new SessionEndPayload(sessionId, winnerId, winCondition, resultData);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session end to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session end", e);
        }
    }

    /**
     * Publishes a session pause event.
     *
     * @param gameId    the game machine identifier
     * @param sessionId the session identifier
     * @param pausedBy  the user who paused the session, or {@code null}
     */
    public void publishPause(String gameId, String sessionId, String pausedBy) {
        try {
            String topic = MqttTopics.sessionPause(buildingId, gameId);
            SessionPausePayload payload = new SessionPausePayload(sessionId, pausedBy);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session pause to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session pause", e);
        }
    }

    /**
     * Publishes a session resume event.
     *
     * @param gameId    the game machine identifier
     * @param sessionId the session identifier
     */
    public void publishResume(String gameId, String sessionId) {
        try {
            String topic = MqttTopics.sessionResume(buildingId, gameId);
            SessionResumePayload payload = new SessionResumePayload(sessionId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            log.info("Publishing session resume to topic {}: {}", topic, payload);
            adapter.publish(topic, bytes, MqttQos.SESSION, false);
        } catch (MqttException e) {
            log.error("Failed to publish session resume", e);
        }
    }
}
