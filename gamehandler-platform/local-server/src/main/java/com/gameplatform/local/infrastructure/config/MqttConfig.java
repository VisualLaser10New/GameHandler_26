package com.gameplatform.local.infrastructure.config;

import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameSessionListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.GameStateListener;
import com.gameplatform.local.infrastructure.adapters.in.mqtt.HeartbeatListener;
import com.gameplatform.local.infrastructure.adapters.out.mqtt.OutboundMessageDeduplicationCache;
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
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.UUID;

/**
 * Configurazione del client MQTT per la comunicazione asincrona tra il server locale e il broker.
 * <p>
 * Gestisce la creazione del client, la configurazione della connettività (incluso TLS/mTLS opzionale),
 * e la sottoscrizione ai topic relativi allo stato delle partite, alle sessioni e agli heartbeat.
 * Integra un meccanismo di deduplicazione per evitare l'elaborazione di messaggi già inviati in uscita.
 * </p>
 *
 * @see com.gameplatform.local.infrastructure.adapters.in.mqtt.GameStateListener
 * @see com.gameplatform.local.infrastructure.adapters.in.mqtt.GameSessionListener
 * @see com.gameplatform.local.infrastructure.adapters.in.mqtt.HeartbeatListener
 * @see com.gameplatform.local.infrastructure.adapters.out.mqtt.OutboundMessageDeduplicationCache
 */
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

    @Value("${mqtt.trust-store:}")
    private String mqttTrustStore;

    @Value("${mqtt.trust-store-password:}")
    private String mqttTrustStorePassword;

    @Value("${mqtt.key-store:}")
    private String mqttKeyStore;

    @Value("${mqtt.key-store-password:}")
    private String mqttKeyStorePassword;

    private final ResourceLoader resourceLoader;
    private final OutboundMessageDeduplicationCache deduplicationCache;

    /**
     * Costruisce una nuova configurazione MQTT con il loader di risorse e la cache di deduplicazione.
     *
     * @param resourceLoader     il loader per la risoluzione dei percorsi delle risorse (truststore, keystore)
     * @param deduplicationCache la cache per la deduplicazione dei messaggi in uscita
     */
    public MqttConfig(ResourceLoader resourceLoader,
                      OutboundMessageDeduplicationCache deduplicationCache) {
        this.resourceLoader = resourceLoader;
        this.deduplicationCache = deduplicationCache;
    }

    /**
     * Crea e configura il client MQTT, stabilendo la connessione al broker e sottoscrivendo
     * i topic per stato partite, sessioni e heartbeat.
     * <p>
     * Se il broker URL utilizza lo schema {@code ssl://} e sono configurati truststore e opzionalmente
     * keystore, configura una connessione TLS/mTLS. I messaggi in ingresso vengono deduplicati tramite
     * la cache di deduplicazione prima di essere inoltrati ai rispettivi listener.
     * </p>
     *
     * @param gameStateListener   listener per i messaggi di stato delle partite
     * @param gameSessionListener listener per i messaggi di sessione delle partite
     * @param heartbeatListener   listener per i messaggi di heartbeat e relativi ack
     * @return il client MQTT connesso e configurato
     * @throws MqttException se si verifica un errore durante la creazione o la connessione del client
     */
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
        if (brokerUrl.startsWith("ssl://") && mqttTrustStore != null && !mqttTrustStore.isBlank()) {
            try {
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                try (InputStream in = resourceLoader.getResource(mqttTrustStore).getInputStream()) {
                    trustStore.load(in, mqttTrustStorePassword.toCharArray());
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                KeyManagerFactory kmf = null;
                if (mqttKeyStore != null && !mqttKeyStore.isBlank()) {
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    try (InputStream in = resourceLoader.getResource(mqttKeyStore).getInputStream()) {
                        keyStore.load(in, mqttKeyStorePassword.toCharArray());
                    }
                    kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(keyStore, mqttKeyStorePassword.toCharArray());
                }

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf != null ? kmf.getKeyManagers() : null, tmf.getTrustManagers(), new java.security.SecureRandom());
                options.setSocketFactory(sslContext.getSocketFactory());
                log.info("Successfully configured SSLSocketFactory (mTLS) for MQTT client");
            } catch (Exception e) {
                log.error("Failed to configure SSLSocketFactory for MQTT client", e);
            }
        }

        // Subscribe to relevant topics
        String gameStateTopic = "building/" + buildingId + "/game/+/state";
        String sessionTopic = "building/" + buildingId + "/game/+/session/#";
        String heartbeatTopic = "building/" + buildingId + "/game/+/heartbeat";
        String heartbeatAckTopic = "building/" + buildingId + "/game/+/heartbeat/ack";

        // Set callback before connecting
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT connection complete (reconnect: {}, serverURI: {})", reconnect, serverURI);
                try {
                    client.subscribe(gameStateTopic, 1, (topic, msg) -> {
                        byte[] payload = msg.getPayload();
                        if (deduplicationCache.isRecentOutbound(topic, payload)) {
                            return;
                        }
                        try {
                            gameStateListener.handleStateMessage(topic, payload);
                        } catch (Exception e) {
                            log.error("Error in GameStateListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(sessionTopic, 1, (topic, msg) -> {
                        byte[] payload = msg.getPayload();
                        if (deduplicationCache.isRecentOutbound(topic, payload)) {
                            log.info("[MqttConfig] DROPPED (dedupe) topic={} payload={}", topic, new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                            return;
                        }
                        try {
                            gameSessionListener.handleSessionMessage(topic, payload);
                        } catch (Exception e) {
                            log.error("Error in GameSessionListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(heartbeatTopic, 0, (topic, msg) -> {
                        byte[] payload = msg.getPayload();
                        if (deduplicationCache.isRecentOutbound(topic, payload)) {
                            return;
                        }
                        try {
                            heartbeatListener.handleHeartbeat(topic, payload);
                        } catch (Exception e) {
                            log.error("Error in HeartbeatListener on topic {}: {}", topic, e.getMessage(), e);
                        }
                    });

                    client.subscribe(heartbeatAckTopic, 0, (topic, msg) -> {
                        byte[] payload = msg.getPayload();
                        if (deduplicationCache.isRecentOutbound(topic, payload)) {
                            return;
                        }
                        try {
                            heartbeatListener.handleHeartbeat(topic, payload);
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

