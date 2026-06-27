package com.gameplatform.local.infrastructure.adapters.out.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.*;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MqttPublisherAdapter implements PublishGameStatePort, PublishAlertPort {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisherAdapter.class);

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final String buildingId;

    public MqttPublisherAdapter(
            IMqttClient mqttClient,
            ObjectMapper objectMapper,
            @Value("${app.building-id}") String buildingId) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.buildingId = buildingId;
    }

    @Override
    public void publishState(GameId gameId, GameMachineStatus status) {
        try {
            String topic = MqttTopics.gameState(buildingId, gameId.id());
            GameStatePayload payload = new GameStatePayload(gameId.id(), status, null);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);
            
            MqttMessage message = new MqttMessage(bytes);
            message.setQos(MqttQos.STATE);
            message.setRetained(true);
            
            log.info("Publishing game state to topic {}: {}", topic, payload);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish game state", e);
        }
    }

    @Override
    public void publishSessionEvent(String topic, Object payload) {
        try {
            byte[] bytes;
            if (payload instanceof GameSession session) {
                if (topic.endsWith("/session/start") || topic.endsWith("/start")) {
                    SessionStartPayload startPayload = new SessionStartPayload(
                            session.getId().value(),
                            session.getGameType(),
                            session.getParticipants().stream().map(UserId::value).toList()
                    );
                    bytes = MqttPayloadSerializer.serialize(startPayload);
                } else if (topic.endsWith("/session/end") || topic.endsWith("/end")) {
                    String resultJson = null;
                    if (session.getResult() != null) {
                        resultJson = objectMapper.writeValueAsString(session.getResult());
                    }
                    SessionEndPayload endPayload = new SessionEndPayload(
                            session.getId().value(),
                            session.getWinnerId() != null ? session.getWinnerId().value() : null,
                            session.getWinCondition(),
                            resultJson
                    );
                    bytes = MqttPayloadSerializer.serialize(endPayload);
                } else if (topic.endsWith("/session/pause") || topic.endsWith("/pause")) {
                    SessionPausePayload pausePayload = new SessionPausePayload(
                            session.getId().value(),
                            null
                    );
                    bytes = MqttPayloadSerializer.serialize(pausePayload);
                } else if (topic.endsWith("/session/resume") || topic.endsWith("/resume")) {
                    SessionResumePayload resumePayload = new SessionResumePayload(session.getId().value());
                    bytes = MqttPayloadSerializer.serialize(resumePayload);
                } else {
                    bytes = MqttPayloadSerializer.serialize(session);
                }
            } else {
                bytes = MqttPayloadSerializer.serialize(payload);
            }

            MqttMessage message = new MqttMessage(bytes);
            message.setQos(MqttQos.SESSION);
            message.setRetained(false);

            log.info("Publishing session event to topic {}", topic);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish session event on topic {}", topic, e);
        }
    }

    @Override
    public void publishAlert(AlertPayload payload) {
        try {
            String topic = MqttTopics.alerts(buildingId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            MqttMessage message = new MqttMessage(bytes);
            message.setQos(1); // Default QoS 1 for alerts
            message.setRetained(false);

            log.info("Publishing alert to topic {}: {}", topic, payload);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish alert", e);
        }
    }
}

