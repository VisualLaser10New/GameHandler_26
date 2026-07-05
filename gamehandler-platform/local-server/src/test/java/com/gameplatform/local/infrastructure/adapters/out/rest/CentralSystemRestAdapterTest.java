package com.gameplatform.local.infrastructure.adapters.out.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import javax.net.ssl.SSLContext;

import com.gameplatform.shared.dto.SyncPayloadDto;

class CentralSystemRestAdapterTest {

    private CentralSystemRestAdapter newAdapter(String url) throws Exception {
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, null, new java.security.SecureRandom());
        return new CentralSystemRestAdapter(ssl, url, "test-key");
    }

    @Test
    void isReachableReturnsFalseWhenCentralDown() throws Exception {
        // Use a closed port on localhost to force a connection refused quickly
        CentralSystemRestAdapter adapter = newAdapter("http://127.0.0.1:1");
        assertThat(adapter.isReachable()).isFalse();
    }

    @Test
    void sendSyncPayloadReturnsFalseWhenCentralDown() throws Exception {
        CentralSystemRestAdapter adapter = newAdapter("http://127.0.0.1:1");
        boolean ok = adapter.sendSyncPayload(new SyncPayloadDto("b1", java.util.List.of()));
        assertThat(ok).isFalse();
    }

    @Test
    void sendSyncPayloadWithMalformedUrlReturnsFalse() throws Exception {
        // Malformed host -> UnknownHostException/IllegalArgumentException wrapped -> false
        CentralSystemRestAdapter adapter = newAdapter("http://nonexistent.invalid.domain.xyz");
        boolean ok = adapter.sendSyncPayload(new SyncPayloadDto("b1", java.util.List.of()));
        assertThat(ok).isFalse();
    }

    @Test
    void sendsDefaultSecretApiKeyHeaderWhenPropertyIsAbsent() throws Exception {
        // B2.3 — when the `internal.api-key` property is NOT set, the production wiring
        // @Value("${internal.api-key:secret}") supplies the default "secret". The
        // constructor requires a non-null String, so this unit test simulates the
        // property-absent production behaviour by passing the default "secret" and
        // asserting the X-Internal-Api-Key header sent over the wire is exactly "secret".
        AtomicReference<String> capturedKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/sync/receive", exchange -> {
            capturedKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, null, new java.security.SecureRandom());
            // Pass the @Value default "secret" — mirrors property-absent wiring.
            CentralSystemRestAdapter adapter = new CentralSystemRestAdapter(ssl, url, "secret");
            boolean ok = adapter.sendSyncPayload(new SyncPayloadDto("b1", java.util.List.of()));
            assertThat(ok).isTrue();
            assertThat(capturedKey.get()).isEqualTo("secret");
        } finally {
            server.stop(0);
        }
    }
}
