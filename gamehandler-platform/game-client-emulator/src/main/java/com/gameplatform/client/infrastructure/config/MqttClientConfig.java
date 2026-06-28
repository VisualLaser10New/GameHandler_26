package com.gameplatform.client.infrastructure.config;

/**
 * Configuration POJO for the MQTT client connection.
 * <p>
 * Holds all parameters needed to establish and maintain a connection
 * to an MQTT broker: broker URL, client identifier, building ID,
 * connection timeout, and keep-alive interval.
 */
public class MqttClientConfig {

    private final String brokerUrl;
    private final String clientId;
    private final String buildingId;
    private final int connectionTimeout;
    private final int keepAliveInterval;

    /**
     * Creates a config with default timeout (10 s) and keep-alive (60 s).
     *
     * @param brokerUrl  MQTT broker URL (e.g. {@code tcp://localhost:1883} or {@code ssl://...})
     * @param clientId   base client identifier (a unique suffix is appended at connection time)
     * @param buildingId building identifier used in MQTT topic prefixes
     */
    public MqttClientConfig(String brokerUrl, String clientId, String buildingId) {
        this(brokerUrl, clientId, buildingId, 10, 60);
    }

    /**
     * Creates a config with custom timeout and keep-alive values.
     *
     * @param brokerUrl          MQTT broker URL
     * @param clientId           base client identifier
     * @param buildingId         building identifier
     * @param connectionTimeout  connection timeout in seconds
     * @param keepAliveInterval  keep-alive interval in seconds
     */
    public MqttClientConfig(String brokerUrl, String clientId, String buildingId,
                            int connectionTimeout, int keepAliveInterval) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.buildingId = buildingId;
        this.connectionTimeout = connectionTimeout;
        this.keepAliveInterval = keepAliveInterval;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }
}
