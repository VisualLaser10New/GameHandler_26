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

/**
 * Adattatore che pubblica messaggi MQTT verso il broker per la notifica dello stato di gioco,
 * degli eventi di sessione e degli alert. Implementa le interfacce {@link PublishGameStatePort}
 * e {@link PublishAlertPort} del dominio.
 *
 * <p>Delega la serializzazione dei payload a {@link MqttPayloadSerializer} e utilizza
 * {@link OutboundMessageDeduplicationCache} per evitare il loopback dei messaggi pubblicati
 * sugli stessi topic a cui il server locale è sottoscritto.</p>
 *
 * @see PublishGameStatePort
 * @see PublishAlertPort
 * @see OutboundMessageDeduplicationCache
 * @see MqttPayloadSerializer
 */
@Component
public class MqttPublisherAdapter implements PublishGameStatePort, PublishAlertPort {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisherAdapter.class);

    private final IMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final String buildingId;
    private final OutboundMessageDeduplicationCache deduplicationCache;

    /**
     * Costruisce un nuovo adattatore con il client MQTT, il mapper JSON, l'identificativo
     * dell'edificio e la cache di deduplicazione dei messaggi in uscita.
     *
     * @param mqttClient          client MQTT lazy-inizializzato per la pubblicazione dei messaggi
     * @param objectMapper        mapper JSON per la serializzazione dei risultati di sessione
     * @param buildingId          identificativo dell'edificio, utilizzato per comporre i topic
     * @param deduplicationCache  cache per la deduplicazione dei messaggi in uscita
     * @see OutboundMessageDeduplicationCache
     */
    public MqttPublisherAdapter(
            @org.springframework.context.annotation.Lazy IMqttClient mqttClient,
            ObjectMapper objectMapper,
            @Value("${app.building-id}") String buildingId,
            OutboundMessageDeduplicationCache deduplicationCache) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.buildingId = buildingId;
        this.deduplicationCache = deduplicationCache;
    }

    /**
     * Pubblica lo stato corrente di una macchina da gioco sul topic MQTT dedicato.
     * Il messaggio viene inviato con il flag retained attivo e il QoS configurato per lo stato.
     *
     * @param gameId  identificativo del gioco
     * @param status  stato corrente della macchina da gioco
     * @see MqttTopics#gameState(String, String)
     * @see GameStatePayload
     */
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
            deduplicationCache.recordOutbound(topic, bytes);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish game state", e);
        }
    }

    /**
     * Pubblica un evento di sessione (avvio, termine, pausa, ripresa) sul topic MQTT
     * specificato. Il payload viene serializzato nel formato appropriato in base al
     * tipo di evento desunto dal suffisso del topic.
     *
     * @param topic    topic MQTT su cui pubblicare l'evento
     * @param payload  oggetto contenente i dati dell'evento; se è un'istanza di
     *                 {@link GameSession} viene serializzato con il payload tipizzato
     *                 corrispondente
     * @see SessionStartPayload
     * @see SessionEndPayload
     * @see SessionPausePayload
     * @see SessionResumePayload
     */
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
            deduplicationCache.recordOutbound(topic, bytes);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish session event on topic {}", topic, e);
        }
    }

    /**
     * Pubblica un alert sul topic MQTT degli alert dell'edificio.
     * Il messaggio viene inviato con QoS 1 e senza retained flag.
     *
     * @param payload  payload contenente i dettagli dell'alert
     * @see MqttTopics#alerts(String)
     * @see AlertPayload
     */
    @Override
    public void publishAlert(AlertPayload payload) {
        try {
            String topic = MqttTopics.alerts(buildingId);
            byte[] bytes = MqttPayloadSerializer.serialize(payload);

            MqttMessage message = new MqttMessage(bytes);
            message.setQos(1); // Default QoS 1 for alerts
            message.setRetained(false);

            log.info("Publishing alert to topic {}: {}", topic, payload);
            deduplicationCache.recordOutbound(topic, bytes);
            mqttClient.publish(topic, message);
        } catch (Exception e) {
            log.error("Failed to publish alert", e);
        }
    }
}

