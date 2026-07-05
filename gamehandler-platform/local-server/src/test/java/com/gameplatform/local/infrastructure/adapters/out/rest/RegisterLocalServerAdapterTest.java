package com.gameplatform.local.infrastructure.adapters.out.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class RegisterLocalServerAdapterTest {

    private HttpServer server;
    private final AtomicInteger registrations = new AtomicInteger();

    private RegisterLocalServerAdapter newAdapter(String url) throws Exception {
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, null, new java.security.SecureRandom());
        return new RegisterLocalServerAdapter(ssl, url, "test-key", "building-1", "https://local-server-1:8081");
    }

    private void startServer(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/servers/register", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                registrations.incrementAndGet();
                // Slurp the request body (registration payload)
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void registerReturnsTrueWhenCentralResponds2xx() throws Exception {
        startServer(200);
        RegisterLocalServerAdapter adapter = newAdapter(baseUrl());
        boolean ok = adapter.register();
        assertThat(ok).isTrue();
        assertThat(registrations.get()).isEqualTo(1);
    }

    @Test
    void registerReturnsFalseWhenCentralResponds5xx() throws Exception {
        startServer(500);
        RegisterLocalServerAdapter adapter = newAdapter(baseUrl());
        boolean ok = adapter.register();
        assertThat(ok).isFalse();
        assertThat(registrations.get()).isEqualTo(1);
    }

    @Test
    void registerReturnsFalseWhenCentralUnreachable() throws Exception {
        // closed port → connection refused quickly
        RegisterLocalServerAdapter adapter = newAdapter("http://127.0.0.1:1");
        assertThat(adapter.register()).isFalse();
        // also it must not throw
    }
}
