package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalRestAdapterTest {

    private ResponseEntity<List<UserSyncAckDto>> ackResponse() {
        return new ResponseEntity<>(List.of(new UserSyncAckDto("u1", true, null)), HttpStatus.OK);
    }

    @Test
    void shouldConfigureTimeoutsInDefaultConstructor() throws Exception {
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, null, new java.security.SecureRandom());
        LocalRestAdapter adapter = new LocalRestAdapter(sslContext, "test-api-key", 5000, 5000);

        java.lang.reflect.Field rtField = LocalRestAdapter.class.getDeclaredField("restTemplate");
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

    @Test
    void shouldSuccessfullyPushUsersWithoutRetry() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("building-1"), "http://localhost:8181", Instant.now(), true);
        List<UserSyncDto> users = Collections.singletonList(new UserSyncDto("u1", "user1", "hash", List.of("ROLE_USER")));

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ackResponse());

        List<UserSyncAckDto> acks = adapter.pushUsers(users, server);

        assertThat(acks).hasSize(1);
        assertThat(acks.get(0).applied()).isTrue();
        verify(mockRestTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void shouldRetryOnTransientFailureAndSucceed() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("building-1"), "http://localhost:8181", Instant.now(), true);
        List<UserSyncDto> users = Collections.singletonList(new UserSyncDto("u1", "user1", "hash", List.of("ROLE_USER")));

        // Throw transient network exception on first call, succeed on second
        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new ResourceAccessException("Timeout occurred"))
                .thenReturn(ackResponse());

        adapter.pushUsers(users, server);

        verify(mockRestTemplate, times(2)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void shouldRetryOnTransientHttp500FailureAndEventuallyFail() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("building-1"), "http://localhost:8181", Instant.now(), true);
        List<UserSyncDto> users = Collections.singletonList(new UserSyncDto("u1", "user1", "hash", List.of("ROLE_USER")));

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

        assertThatThrownBy(() -> adapter.pushUsers(users, server))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push users to local server");

        verify(mockRestTemplate, times(3)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void shouldNotRetryOnNonTransientHttp400Failure() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");
        RegisteredLocalServer server = new RegisteredLocalServer(new BuildingId("building-1"), "http://localhost:8181", Instant.now(), true);
        List<UserSyncDto> users = Collections.singletonList(new UserSyncDto("u1", "user1", "hash", List.of("ROLE_USER")));

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        assertThatThrownBy(() -> adapter.pushUsers(users, server))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push users to local server");

        verify(mockRestTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }
}
