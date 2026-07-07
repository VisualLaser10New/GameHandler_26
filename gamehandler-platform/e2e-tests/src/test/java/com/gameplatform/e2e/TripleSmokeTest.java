package com.gameplatform.e2e;

import com.gameplatform.e2e.harness.TestClientEmulator;
import com.gameplatform.e2e.harness.TripleContextTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Triple-context smoke test — verifies that a {@link TestClientEmulator} can
 * connect to the embedded Moquette broker while both the central and local
 * Spring contexts are running.
 */
@DisplayName("Triple-context smoke test — client emulator connects to Moquette")
class TripleSmokeTest extends TripleContextTestBase {

    @Test
    @DisplayName("A client emulator connects to the Moquette broker")
    void clientEmulatorConnectsToBroker() {
        TestClientEmulator client = newClient("test-client");

        assertThat(client.isConnected())
                .as("client emulator must be connected to the broker")
                .isTrue();

        try {
            client.disconnect();
        } catch (Exception e) {
            // best-effort disconnect; don't fail the test on cleanup
        }
    }
}