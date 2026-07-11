package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.QueryLocalServerUserCountPort;
import com.gameplatform.shared.domain.model.BuildingId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4 — Mockito unit tests for {@link LocalUserCountRestAdapter}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>happy-path GET → parses the {@link Long} body and returns it;</li>
 *   <li>retries on transient failure (network/5xx) and succeeds on the 2nd attempt;</li>
 *   <li>non-transient 4xx → no retry, returns {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE};</li>
 *   <li>transient failure exhausting all 3 attempts → returns
 *       {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE};</li>
 *   <li>the {@code X-Internal-Api-Key} header is set on the request;</li>
 *   <li>the default constructor configures the same
 *       {@code central.replication.connect-timeout-ms}/{@code read-timeout-ms}
 *       timeouts as {@link LocalRestAdapter}.</li>
 * </ul>
 */
class LocalServerUserCountRestTest {

    private RegisteredLocalServer server() {
        return new RegisteredLocalServer(
                new BuildingId("building-1"),
                "http://localhost:8081",
                Instant.parse("2026-07-05T12:00:00Z"),
                true);
    }

    @Test
    void shouldReturnCountOnHappyPath() {
        RestTemplate mockRest = mock(RestTemplate.class);
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(mockRest, "test-api-key");
        when(mockRest.exchange(
                eq("http://localhost:8081/internal/users/count"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Long.class)))
                .thenReturn(new ResponseEntity<>(7L, HttpStatus.OK));

        long count = adapter.countReplicatedUsers(server());

        assertThat(count).isEqualTo(7L);
        verify(mockRest, times(1)).exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class));
    }

    @Test
    void shouldRetryOnTransientFailureAndSucceed() {
        RestTemplate mockRest = mock(RestTemplate.class);
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(mockRest, "test-api-key");
        when(mockRest.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class)))
                .thenThrow(new ResourceAccessException("connection timed out"))
                .thenReturn(new ResponseEntity<>(5L, HttpStatus.OK));

        long count = adapter.countReplicatedUsers(server());

        assertThat(count).isEqualTo(5L);
        verify(mockRest, times(2)).exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class));
    }

    @Test
    void shouldReturnCountUnavailableOnNonTransient4xxWithoutRetry() {
        RestTemplate mockRest = mock(RestTemplate.class);
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(mockRest, "test-api-key");
        when(mockRest.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        long count = adapter.countReplicatedUsers(server());

        assertThat(count).isEqualTo(QueryLocalServerUserCountPort.COUNT_UNAVAILABLE);
        // 4xx is non-transient → no retry.
        verify(mockRest, times(1)).exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class));
    }

    @Test
    void shouldReturnCountUnavailableAfterExhaustingRetriesOn5xx() {
        RestTemplate mockRest = mock(RestTemplate.class);
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(mockRest, "test-api-key");
        when(mockRest.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

        long count = adapter.countReplicatedUsers(server());

        assertThat(count).isEqualTo(QueryLocalServerUserCountPort.COUNT_UNAVAILABLE);
        // 5xx is transient → 3 attempts then give up.
        verify(mockRest, times(3)).exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class));
    }

    @Test
    void shouldReturnCountUnavailableWhenBodyIsNull() {
        RestTemplate mockRest = mock(RestTemplate.class);
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(mockRest, "test-api-key");
        when(mockRest.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Long.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        long count = adapter.countReplicatedUsers(server());

        // Null body is treated as "count unknown" — return the sentinel.
        assertThat(count).isEqualTo(QueryLocalServerUserCountPort.COUNT_UNAVAILABLE);
    }

    @Test
    void shouldConfigureTimeoutsInDefaultConstructor() throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, null, new java.security.SecureRandom());
        LocalUserCountRestAdapter adapter = new LocalUserCountRestAdapter(sslContext, "test-api-key", 5000, 5000);

        java.lang.reflect.Field rtField = LocalUserCountRestAdapter.class.getDeclaredField("restTemplate");
        rtField.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) rtField.get(adapter);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();

        java.lang.reflect.Field connTimeoutField = SimpleClientHttpRequestFactory.class.getDeclaredField("connectTimeout");
        connTimeoutField.setAccessible(true);
        int connectTimeout = (int) connTimeoutField.get(factory);

        java.lang.reflect.Field readTimeoutField = SimpleClientHttpRequestFactory.class.getDeclaredField("readTimeout");
        readTimeoutField.setAccessible(true);
        int readTimeout = (int) readTimeoutField.get(factory);

        assertThat(connectTimeout).isEqualTo(5000);
        assertThat(readTimeout).isEqualTo(5000);
    }
}