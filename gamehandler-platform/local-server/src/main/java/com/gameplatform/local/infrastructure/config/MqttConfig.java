package com.gameplatform.local.infrastructure.config;

import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameSessionListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameStateListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.HeartbeatListener;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLSocketFactory;
import java.util.UUID;

@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.building-id}")
    private String buildingId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Bean(destroyMethod = "disconnect")
    public IMqttClient mqttClient(
            @org.springframework.context.annotation.Lazy GameStateListener gameStateListener,
            @org.springframework.context.annotation.Lazy GameSessionListener gameSessionListener,
            @org.springframework.context.annotation.Lazy HeartbeatListener heartbeatListener) throws MqttException {

        String clientId = "local-server-" + buildingId + "-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Creating MqttClient with brokerUrl: {} and clientId: {}", brokerUrl, clientId);

        MqttClient client = new MqttClient(brokerUrl, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);

        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }

        // Configure TLS if brokerUrl uses ssl scheme
        if (brokerUrl.startsWith("ssl://")) {
            try {
                options.setSocketFactory(SSLSocketFactory.getDefault());
            } catch (Exception e) {
                log.error("Failed to configure default SSLSocketFactory for MQTT client", e);
            }
        }

        // Subscribe to relevant topics
        String gameStateTopic = "building/" + buildingId + "/game/+/state";
        String sessionTopic = "building/" + buildingId + "/game/+/session/+";
        String heartbeatTopic = "building/" + buildingId + "/game/+/heartbeat";
        String heartbeatAckTopic = "building/" + buildingId + "/game/+/heartbeat/ack";

        // Set callback before connecting
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connection complete (reconnect: {}, serverURI: {})", reconnect, serverURI);
                try {
                    client.subscribe(gameStateTopic, 1, (topic, msg) -> {
                        try {
                            gameStateListener.handleStateMessage(topic, msg.getPayload());
                        } catch (Exception e) {
                            log.error("Error in GameStateListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(sessionTopic, 1, (topic, msg) -> {
                        try {
                            gameSessionListener.handleSessionMessage(topic, msg.getPayload());
                        } catch (Exception e) {
                            log.error("Error in GameSessionListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(heartbeatTopic, 0, (topic, msg) -> {
                        try {
                            heartbeatListener.handleHeartbeat(topic, msg.getPayload());
                        } catch (Exception e) {
                            log.error("Error in HeartbeatListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(heartbeatAckTopic, 0, (topic, msg) -> {
                        try {
                            heartbeatListener.handleHeartbeat(topic, msg.getPayload());
                        } catch (Exception e) {
                            log.error("Error in HeartbeatListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    log.info("MQTT Client subscribed to topics successfully");
                } catch (MqttException e) {
                    log.error("Failed to subscribe to topics on connectComplete", e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // Not used since we use per-subscription message handlers
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used
            }
        });

        client.connect(options);
        log.info("MQTT Client connected successfully");
        return client;
    }
}

