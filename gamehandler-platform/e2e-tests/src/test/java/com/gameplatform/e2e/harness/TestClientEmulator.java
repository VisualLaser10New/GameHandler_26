package com.gameplatform.e2e.harness;

import com.gameplatform.client.infrastructure.config.MqttClientConfig;
import com.gameplatform.client.infrastructure.mqtt.GameStatePublisher;
import com.gameplatform.client.infrastructure.mqtt.HeartbeatPublisher;
import com.gameplatform.client.infrastructure.mqtt.MqttClientAdapter;
import com.gameplatform.client.infrastructure.mqtt.SessionPublisher;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Lightweight test wrapper around the production client-emulator MQTT classes.
 *
 * <p>Constructs a {@link MqttClientConfig} → {@link MqttClientAdapter} → the three
 * production publishers ({@link SessionPublisher}, {@link HeartbeatPublisher},
 * {@link GameStatePublisher}) exactly as the real emulator would, but without
 * any JavaFX UI. Call {@link #connect()} to establish the Paho MQTT connection
 * to the broker, then use the publisher accessors to drive session/state/heartbeat
 * events. Call {@link #disconnect()} when done.</p>
 *
 * <p>This wrapper is intentionally thin — it does NOT implement the full client
 * emulator lifecycle (login, game catalog, UI). It only provides the MQTT
 * publish surface that e2e tests need to simulate client→local-server
 * interactions over the broker.</p>
 */
public class TestClientEmulator {

    private final MqttClientAdapter adapter;
    private final SessionPublisher sessionPublisher;
    private final HeartbeatPublisher heartbeatPublisher;
    private final GameStatePublisher gameStatePublisher;

    /**
     * Creates (but does not connect) a test client emulator.
     *
     * @param brokerUrl  the MQTT broker URL (e.g. {@code tcp://localhost:1883})
     * @param buildingId the building id used in MQTT topic prefixes
     * @param clientId   the base client identifier (a unique suffix is appended at connect time)
     */
    public TestClientEmulator(String brokerUrl, String buildingId, String clientId) {
        MqttClientConfig config = new MqttClientConfig(brokerUrl, clientId, buildingId);
        this.adapter = new MqttClientAdapter(config);
        this.sessionPublisher = new SessionPublisher(adapter, buildingId);
        this.heartbeatPublisher = new HeartbeatPublisher(adapter, buildingId);
        this.gameStatePublisher = new GameStatePublisher(adapter, buildingId);
    }

    /**
     * Connects to the MQTT broker.
     *
     * @throws MqttException if the connection fails
     */
    public void connect() throws MqttException {
        adapter.connect();
    }

    /**
     * Disconnects from the MQTT broker.
     *
     * @throws MqttException if the disconnect fails
     */
    public void disconnect() throws MqttException {
        adapter.disconnect();
    }

    /**
     * Returns whether the client is currently connected to the broker.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return adapter.isConnected();
    }

    /**
     * Returns the session publisher for sending session lifecycle events.
     *
     * @return the {@link SessionPublisher}
     */
    public SessionPublisher getSessionPublisher() {
        return sessionPublisher;
    }

    /**
     * Returns the heartbeat publisher for sending periodic heartbeats.
     *
     * @return the {@link HeartbeatPublisher}
     */
    public HeartbeatPublisher getHeartbeatPublisher() {
        return heartbeatPublisher;
    }

    /**
     * Returns the game state publisher for sending machine state changes.
     *
     * @return the {@link GameStatePublisher}
     */
    public GameStatePublisher getGameStatePublisher() {
        return gameStatePublisher;
    }

    /**
     * Returns the underlying MQTT adapter (for advanced publish/subscribe use).
     *
     * @return the {@link MqttClientAdapter}
     */
    public MqttClientAdapter getAdapter() {
        return adapter;
    }
}
