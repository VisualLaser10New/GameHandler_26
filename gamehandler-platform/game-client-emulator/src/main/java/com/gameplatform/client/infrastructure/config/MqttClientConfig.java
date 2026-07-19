package com.gameplatform.client.infrastructure.config;

/**
 * Configurazione POJO per la connessione del client MQTT.
 * <p>
 * Contiene tutti i parametri necessari per stabilire e mantenere una connessione
 * verso un broker MQTT: URL del broker, identificativo del client, identificativo
 * dell'edificio, timeout di connessione e intervallo di keep-alive.
 */
public class MqttClientConfig {

    /**
     * URL predefinito del broker MQTT.
     * <p>
     * Il valore corrisponde a {@code tcp://localhost:1883}.
     */
    public static final String DEFAULT_BROKER_URL = "tcp://localhost:1883";

    private final String brokerUrl;
    private final String clientId;
    private final String buildingId;
    private final int connectionTimeout;
    private final int keepAliveInterval;

    /**
     * Crea una configurazione con timeout di connessione predefinito (10 secondi)
     * e intervallo di keep-alive predefinito (60 secondi).
     *
     * @param brokerUrl  URL del broker MQTT, ad esempio {@code tcp://localhost:1883}
     *                   o {@code ssl://broker.example.com:8883}; non {@code null}
     * @param clientId   identificativo base del client; un suffisso univoco viene
     *                   aggiunto al momento della connessione; non {@code null}
     * @param buildingId identificativo dell'edificio utilizzato nei prefissi dei
     *                   topic MQTT; non {@code null}
     */
    public MqttClientConfig(String brokerUrl, String clientId, String buildingId) {
        this(brokerUrl, clientId, buildingId, 10, 60);
    }

    /**
     * Crea una configurazione con timeout di connessione e intervallo di keep-alive
     * personalizzati.
     *
     * @param brokerUrl          URL del broker MQTT; non {@code null}
     * @param clientId           identificativo base del client; non {@code null}
     * @param buildingId         identificativo dell'edificio; non {@code null}
     * @param connectionTimeout  timeout di connessione in secondi; deve essere
     *                           maggiore di zero
     * @param keepAliveInterval  intervallo di keep-alive in secondi; deve essere
     *                           maggiore di zero
     */
    public MqttClientConfig(String brokerUrl, String clientId, String buildingId,
                            int connectionTimeout, int keepAliveInterval) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.buildingId = buildingId;
        this.connectionTimeout = connectionTimeout;
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Restituisce l'URL del broker MQTT.
     *
     * @return URL del broker MQTT; può essere {@code null} se non impostato
     */
    public String getBrokerUrl() {
        return brokerUrl;
    }

    /**
     * Restituisce l'identificativo base del client.
     *
     * @return identificativo del client; può essere {@code null} se non impostato
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Restituisce l'identificativo dell'edificio.
     *
     * @return identificativo dell'edificio; può essere {@code null} se non impostato
     */
    public String getBuildingId() {
        return buildingId;
    }

    /**
     * Restituisce il timeout di connessione in secondi.
     *
     * @return timeout di connessione in secondi
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Restituisce l'intervallo di keep-alive in secondi.
     *
     * @return intervallo di keep-alive in secondi
     */
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }
}
