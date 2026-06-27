package com.gameplatform.local.infrastructure.adapters.out.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.Test;

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
}
