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

/**
 * Listener MQTT per i messaggi di heartbeat provenienti dai client di gioco.
 * <p>
 * Gestisce sia le richieste di heartbeat (client-initiated), per le quali risponde con un
 * messaggio ACK sul topic dedicato, sia le notifiche ACK (server-initiated). In entrambi i casi
 * registra il heartbeat presso {@link HealthCheckService} per la verifica dello stato di salute
 * e, per gli ACK, notifica anche {@link SessionRecoveryService}.
 * </p>
 *
 * @see HealthCheckService
 * @see SessionRecoveryService
 * @see PublishGameStatePort
 */
@Component
public class HeartbeatListener {

    private final HealthCheckService healthCheckService;
    private final SessionRecoveryService sessionRecoveryService;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;

    /**
     * Costruisce un listener per gli heartbeat con i servizi di health check e recovery,
     * il port di pubblicazione degli eventi e l'orologio di sistema.
     *
     * @param healthCheckService    servizio per la registrazione degli heartbeat dei giocatori
     * @param sessionRecoveryService servizio per la registrazione degli ACK di heartbeat
     * @param publishGameStatePort  port per la pubblicazione dei messaggi ACK sul broker MQTT
     * @param clock                 orologio di sistema per la generazione dei timestamp
     */
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

    /**
     * Elabora un messaggio MQTT di heartbeat, distinguendo tra richiesta di heartbeat
     * (client-initiated) e notifica ACK (server-initiated).
     * <p>
     * Per una richiesta di heartbeat registra il battito presso {@link HealthCheckService} e
     * pubblica un messaggio ACK sul topic di ritorno. Per una notifica ACK registra il
     * heartbeat sia su {@link HealthCheckService} sia su {@link SessionRecoveryService}.
     * </p>
     *
     * @param topic   topic MQTT dal quale estrarre l'identificativo del gioco e dell'edificio
     * @param payload payload del messaggio (non utilizzato direttamente; l'azione è determinata
     *                dall'ultimo segmento del topic)
     * @see MqttTopics#heartbeatAck(String, String)
     * @see HeartbeatAckPayload
     */
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
