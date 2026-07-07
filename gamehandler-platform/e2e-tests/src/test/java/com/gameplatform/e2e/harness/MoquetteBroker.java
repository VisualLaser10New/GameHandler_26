package com.gameplatform.e2e.harness;

import io.moquette.BrokerConstants;
import io.moquette.broker.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Embedded MQTT broker for e2e integration tests, backed by Moquette 0.15.
 *
 * <p>Starts on a dynamically-allocated free port (found via {@link ServerSocket}(0)
 * before Moquette binds). Provides {@link #start()}, {@link #stop()} and
 * {@link #getPort()}. Call {@code start()} <b>before</b> booting the local
 * Spring context, then pass {@code tcp://localhost:<getPort()>} as
 * {@code mqtt.broker-url} to the local context.</p>
 *
 * <p>The broker runs in-memory (no persistent store) and accepts anonymous
 * connections, which is all the e2e harness needs.</p>
 */
public class MoquetteBroker {

    private Server server;
    private int port;

    /**
     * Starts the Moquette broker on a random free port.
     *
     * @throws IOException if Moquette fails to start or no free port can be found
     */
    public void start() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Properties props = new Properties();
        props.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(port));
        props.setProperty(BrokerConstants.HOST_PROPERTY_NAME, "0.0.0.0");
        props.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        // Intentionally NOT setting PERSISTENT_STORE_PROPERTY_NAME → in-memory store

        server = new Server();
        server.startServer(props);
    }

    /**
     * Stops the Moquette broker and releases the port.
     */
    public void stop() {
        if (server != null) {
            server.stopServer();
            server = null;
        }
    }

    /**
     * Returns the port the broker is listening on. Only valid after {@link #start()}.
     *
     * @return the MQTT TCP port
     */
    public int getPort() {
        return port;
    }
}
