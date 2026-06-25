package com.gameplatform.local.infrastructure.adapters.in.mqtt;

import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SessionRecoveryService;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.HeartbeatAckPayload;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class HeartbeatListener {

    private final HealthCheckService healthCheckService;
    private final SessionRecoveryService sessionRecoveryService;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;

    public HeartbeatListener(
            HealthCheckService healthCheckService,
            SessionRecoveryService sessionRecoveryService,
            PublishGameStatePort publishGameStatePort,
            Clock clock) {
        this.healthCheckService = healthCheckService;
        this.sessionRecoveryService = sessionRecoveryService;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
    }

    public void handleHeartbeat(String topic, byte[] payload) {
        String[] tokens = topic.split("/");
        String buildingId = tokens[1];
        String gameId = tokens[3];
        String leaf = tokens[tokens.length - 1];

        GameId targetGameId = new GameId(gameId);

        if ("ack".equals(leaf)) {
            // Heartbeat ACK from client (server-initiated heartbeat reply)
            healthCheckService.registerHeartbeat(targetGameId);
            sessionRecoveryService.registerHeartbeatAck(targetGameId);
        } else {
            // Heartbeat request from client (client-initiated heartbeat)
            healthCheckService.registerHeartbeat(targetGameId);

            // Respond with Heartbeat ACK
            String ackTopic = MqttTopics.heartbeatAck(buildingId, gameId);
            HeartbeatAckPayload ackPayload = new HeartbeatAckPayload(gameId, Instant.now(clock));
            publishGameStatePort.publishSessionEvent(ackTopic, ackPayload);
        }
    }
}
