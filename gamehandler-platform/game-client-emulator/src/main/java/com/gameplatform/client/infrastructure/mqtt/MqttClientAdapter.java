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
 * Wrapper di basso livello attorno a {@link IMqttClient} di Eclipse Paho.
 * <p>
 * Fornisce un'API semplificata per connettersi, disconnettersi, pubblicare
 * e sottoscrivere topic su un broker MQTT. Delega la logica di lifecycle e
 * riconnessione a {@link MqttConnectionManager}.
 * <p>
 * Supporta sia TCP semplice ({@code tcp://}) sia TLS ({@code ssl://}) per l'URL del broker.
 * È possibile registrare un {@link MqttCallbackExtended} esterno tramite
 * {@link #setCallback(MqttCallbackExtended)} per ricevere eventi di connessione
 * e consegna dei messaggi.
 */
public class MqttClientAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqttClientAdapter.class);

    private final MqttClientConfig config;
    private IMqttClient mqttClient;
    private MqttCallbackExtended callback;
    private boolean connected;

    private final java.util.List<java.util.function.Consumer<Boolean>> connectionListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Costruisce un nuovo adapter per la configurazione data.
     *
     * @param config la configurazione del client MQTT (URL del broker, client ID, edificio, timeout)
     */
    public MqttClientAdapter(MqttClientConfig config) {
        this.config = config;
        this.connected = false;
    }

    /**
     * Connette il client al broker MQTT.
     * <p>
     * Crea una nuova istanza di {@link MqttClient} con un client ID univoco
     * (client ID base pi&ugrave; suffisso casuale), configura la riconnessione automatica,
     * la sessione pulita e TLS se l'URL del broker inizia con {@code ssl://},
     * quindi stabilisce la connessione.
     *
     * @throws MqttException se la connessione fallisce
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

                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                try (java.io.InputStream in = new java.io.FileInputStream(truststoreFile)) {
                    trustStore.load(in, "changeit".toCharArray());
                }
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

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
     * Disconnette il client dal broker MQTT.
     *
     * @throws MqttException se la disconnessione fallisce
     */
    public void disconnect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            log.info("Disconnecting from MQTT broker");
            mqttClient.disconnect();
        }
        connected = false;
    }

    /**
     * Pubblica un messaggio sul topic specificato.
     *
     * @param topic    il topic MQTT su cui pubblicare
     * @param payload  il payload del messaggio come array di byte
     * @param qos      il livello di Quality of Service (0, 1 o 2)
     * @param retained se {@code true}, il broker mantiene il messaggio come ultimo valore valido
     * @throws MqttException         se la pubblicazione fallisce
     * @throws IllegalStateException se il client non è connesso
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
     * Sottoscrive un topic con un listener per i messaggi in arrivo.
     *
     * @param topic    il filtro del topic da sottoscrivere (può contenere wildcard)
     * @param qos      il livello QoS massimo per la sottoscrizione
     * @param listener il listener che riceve i messaggi in arrivo
     * @throws MqttException         se la sottoscrizione fallisce
     * @throws IllegalStateException se il client non è connesso
     * @see #subscribe(String, int)
     */
    public void subscribe(String topic, int qos, IMqttMessageListener listener) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        mqttClient.subscribe(topic, qos, listener);
    }

    /**
     * Sottoscrive un topic senza un listener specifico.
     *
     * @param topic il filtro del topic da sottoscrivere
     * @param qos   il livello QoS massimo per la sottoscrizione
     * @throws MqttException         se la sottoscrizione fallisce
     * @throws IllegalStateException se il client non è connesso
     * @see #subscribe(String, int, IMqttMessageListener)
     */
    public void subscribe(String topic, int qos) throws MqttException {
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        mqttClient.subscribe(topic, qos);
    }

    /**
     * Annulla la sottoscrizione da un topic.
     *
     * @param topic il filtro del topic da cui cancellare la sottoscrizione
     * @throws MqttException se l'annullamento della sottoscrizione fallisce
     */
    public void unsubscribe(String topic) throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.unsubscribe(topic);
        }
    }

    /**
     * Registra un callback esterno per eventi di connessione e consegna messaggi.
     * <p>
     * Il {@link MqttCallbackExtended} interno delega a questo callback,
     * consentendo a componenti esterni (es. {@link MqttConnectionManager}) di
     * reagire agli eventi {@code connectComplete}, {@code connectionLost},
     * {@code messageArrived} e {@code deliveryComplete}.
     *
     * @param callback il callback a cui delegare; pu&ograve; essere {@code null}
     */
    public void setCallback(MqttCallbackExtended callback) {
        this.callback = callback;
    }

    /**
     * Registra un listener invocato a ogni (ri)connessione al broker.
     * <p>
     & utile per ripristinare le sottoscrizioni che Paho perde durante la
     * riconnessione (le sessioni pulite non le persistono). Se il client è già
     * connesso al momento della registrazione, il listener viene invocato
     * immediatamente.
     *
     * @param listener un consumer che riceve {@code true} se si tratta di una
     *                 riconnessione, {@code false} per la connessione iniziale
     */
    public void addConnectionListener(java.util.function.Consumer<Boolean> listener) {
        connectionListeners.add(listener);
        if (isConnected()) {
            try { listener.accept(false); } catch (Exception ex) { log.warn("connection listener error", ex); }
        }
    }

    /**
     * Restituisce {@code true} se il client è attualmente connesso al broker.
     *
     * @return {@code true} se connesso, {@code false} altrimenti
     */
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Restituisce la configurazione utilizzata da questo adapter.
     *
     * @return la configurazione del client MQTT
     */
    public MqttClientConfig getConfig() {
        return config;
    }

    /**
     * Restituisce l'istanza del client Paho sottostante.
     *
     * @return l'istanza {@link IMqttClient}, oppure {@code null} se non ancora connesso
     */
    public IMqttClient getMqttClient() {
        return mqttClient;
    }
}
