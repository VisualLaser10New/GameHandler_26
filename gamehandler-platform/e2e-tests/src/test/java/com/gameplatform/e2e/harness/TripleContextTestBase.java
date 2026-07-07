package com.gameplatform.e2e.harness;

import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Extends {@link DualContextTestBase} and adds the ability to create
 * {@link TestClientEmulator} instances connected to the embedded Moquette
 * broker.
 *
 * <p>This is the "triple context" base: central (HTTP) + local (HTTP+MQTT) +
 * client emulator (MQTT only). Tests that need to simulate client→local-server
 * MQTT interactions should extend this class and call {@link #newClient}.</p>
 */
public abstract class TripleContextTestBase extends DualContextTestBase {

    /**
     * Creates a test client emulator for {@code building-1} and connects it to
     * the Moquette broker.
     *
     * @param clientId the base client identifier (a unique suffix is appended at connect time)
     * @return a connected {@link TestClientEmulator}
     */
    protected TestClientEmulator newClient(String clientId) {
        return newClient("building-1", clientId);
    }

    /**
     * Creates a test client emulator for the given building and connects it to
     * the Moquette broker.
     *
     * @param buildingId the building id used in MQTT topic prefixes
     * @param clientId   the base client identifier (a unique suffix is appended at connect time)
     * @return a connected {@link TestClientEmulator}
     */
    protected TestClientEmulator newClient(String buildingId, String clientId) {
        TestClientEmulator client = new TestClientEmulator(
                "tcp://localhost:" + moquette.getPort(),
                buildingId,
                clientId);
        try {
            client.connect();
        } catch (MqttException e) {
            throw new RuntimeException("Failed to connect test client emulator to broker at tcp://localhost:"
                    + moquette.getPort(), e);
        }
        return client;
    }
}
