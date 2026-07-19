package com.gameplatform.client.infrastructure.mqtt;

import com.gameplatform.client.infrastructure.config.MqttClientConfig;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.security.KeyStore;
import java.util.UUID;

/**
 * Low-level wrapper around the Eclipse Paho {@link IMqttClient}.
 * <p>
 * Provides a simplified API for connecting, disconnecting, publishing,
 * and subscribing to an MQTT broker. Delegates lifecycle and reconnection
 * logic to {@link MqttConnectionManager}.
 * <p>
 * Supports both plain TCP ({@code tcp://}) and TLS ({@code ssl://}) broker URLs.
 * An external {@link MqttCallbackExtended} can be registered via
 * {@link #setCallback(MqttCallbackExtended)} to receive connection and
 * message delivery events.
 */
public class MqttClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttClientAdapter.class);

    private final MqttClientConfig config;
    private IMqttClient mqttClient;
    private MqttCallbackExtended callback;
    private boolean connected;

    // Optional listeners notified on every (re)connect so subscribers can
    // re-establish their topic subscriptions (Paho does not restore them).
    private final java.util.List<java.util.function.Consumer<Boolean>> connectionListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Creates a new adapter for the given configuration.
     *
     * @param config the MQTT client configuration (broker URL, client ID, building ID, timeouts)
     */
    public MqttClientAdapter(MqttClientConfig config) {
        this.config = config;
        this.connected = false;
    }

    /**
     * Connects to the MQTT broker.
     * <p>
     * Creates a new {@link MqttClient} instance with a unique client ID
     * (base client ID plus random suffix), configures automatic reconnect,
     * clean session, and TLS if the broker URL starts with {@code ssl://},
     * then establishes the connection.
     *
     * @throws MqttException if the connection fails
     */
    public void connect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            log.warn("Already connected to MQTT broker");
            return;
        }

        String clientId = config.getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Connecting to MQTT broker at {} with clientId {}", config.getBrokerUrl(), clientId);

        mqttClient = new MqttClient(config.getBrokerUrl(), clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(config.getConnectionTimeout());
        options.setKeepAliveInterval(config.getKeepAliveInterval());

        if (config.getBrokerUrl().startsWith("ssl://")) {
            try {
                String gameId = System.getenv().getOrDefault("GAME_ID", "game-1");
                File keystoreFile = new File("certs/" + gameId + "-keystore.p12");
                File truststoreFile = new File("certs/local-truststore.p12");

                // Truststore
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                try (java.io.InputStream in = new java.io.FileInputStream(truststoreFile)) {
                    trustStore.load(in, "changeit".toCharArray());
                }
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                // Keystore
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (java.io.InputStream in = new java.io.FileInputStream(keystoreFile)) {
                    keyStore.load(in, "changeit".toCharArray());
                }
                javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                        javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "changeit".toCharArray());

                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new java.security.SecureRandom());

                options.setSocketFactory(sslContext.getSocketFactory());
                log.info("Configured Paho MQTT client with mTLS keystore and truststore");
            } catch (Exception e) {
                log.error("Failed to load mTLS certificates for MQTT broker: {}", e.getMessage(), e);
                options.setSocketFactory(SSLSocketFactory.getDefault());
            }
        }

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                log.info("MQTT connected (reconnect: {}, server: {})", reconnect, serverURI);
                if (callback != null) {
                    callback.connectComplete(reconnect, serverURI);
                }
                for (java.util.function.Consumer<Boolean> l : connectionListeners) {
                    try { l.accept(reconnect); } catch (Exception ex) { log.warn("connection listener error", ex); }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                connected = false;
                log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
                if (callback != null) {
                    callback.connectionLost(cause);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if (callback != null) {
                    callback.messageArrived(topic, message);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                if (callback != null) {
                    callback.deliveryComplete(token);
                }
            }
        });

        mqttClient.connect(options);
        connected = mqttClient.isConnected();
    }

    /**
     * Disconnects from the MQTT broker.
     *
     * @throws MqttException if the disconnect fails
     */
    public void disconnect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            log.info("Disconnecting from MQTT broker");
            mqttClient.disconnect();
        }
        connected = false;
    }

    /**
     * Publishes a message on the given topic.
     *
     * @param topic    the MQTT topic to publish on
     * @param payload  the message payload as a byte array
     * @param qos      the Quality of Service level (0, 1, or 2)
     * @param retained whether the broker should retain the message
     * @throws MqttException          if the publication fails
     * @throws IllegalStateException  if the client is not connected
     */
    public void publish(String topic, byte[] payload, int qos, boolean retained) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos);
        message.setRetained(retained);
        mqttClient.publish(topic, message);
    }

    /**
     * Subscribes to a topic with a message listener.
     *
     * @param topic    the topic filter to subscribe to (may contain wildcards)
     * @param qos      the maximum QoS level for the subscription
     * @param listener the listener that will receive incoming messages
     * @throws MqttException          if the subscription fails
     * @throws IllegalStateException  if the client is not connected
     */
    public void subscribe(String topic, int qos, IMqttMessageListener listener) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        mqttClient.subscribe(topic, qos, listener);
    }

    /**
     * Subscribes to a topic without a specific listener.
     *
     * @param topic  the topic filter to subscribe to
     * @param qos    the maximum QoS level for the subscription
     * @throws MqttException          if the subscription fails
     * @throws IllegalStateException  if the client is not connected
     */
    public void subscribe(String topic, int qos) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        mqttClient.subscribe(topic, qos);
    }

    /**
     * Unsubscribes from a topic.
     *
     * @param topic  the topic filter to unsubscribe from
     * @throws MqttException if the unsubscribing fails
     */
    public void unsubscribe(String topic) throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.unsubscribe(topic);
        }
    }

    /**
     * Registers an external callback for connection events and message delivery.
     * <p>
     * The internal {@link MqttCallbackExtended} delegates to this callback,
     * allowing external components (e.g. {@link MqttConnectionManager}) to
     * react to {@code connectComplete}, {@code connectionLost},
     * {@code messageArrived}, and {@code deliveryComplete} events.
     *
     * @param callback the callback to delegate to (it may be {@code null})
     */
    public void setCallback(MqttCallbackExtended callback) {
        this.callback = callback;
    }

    /**
     * Registers a listener invoked on every successful (re)connection to the
     * broker. Useful for re-establishing subscriptions that Paho drops on
     * reconnect (clean sessions do not persist them).
     *
     * @param listener a consumer receiving {@code reconnect} (true if this was
     *                 a reconnect rather than the initial connection)
     */
    public void addConnectionListener(java.util.function.Consumer<Boolean> listener) {
        connectionListeners.add(listener);
        // If already connected at registration time, fire immediately so the
        // listener can subscribe without waiting for the next (re)connect.
        if (isConnected()) {
            try { listener.accept(false); } catch (Exception ex) { log.warn("connection listener error", ex); }
        }
    }

    /**
     * Returns whether the client is currently connected to the broker.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Returns the configuration used by this adapter.
     *
     * @return the MQTT client configuration
     */
    public MqttClientConfig getConfig() {
        return config;
    }

    /**
     * Returns the underlying Paho MQTT client instance.
     *
     * @return the {@link IMqttClient} instance, or {@code null} if not yet connected
     */
    public IMqttClient getMqttClient() {
        return mqttClient;
    }
}
