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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates the non-blocking retry behavior of {@link LocalRestAdapter} without
 * spinning up a Spring context: the adapter delegates retrying to a programmatic
 * {@link org.springframework.retry.support.RetryTemplate}, which works whether or not
 * an AOP proxy is present.
 */
class LocalRestAdapterRetryTest {

    private RegisteredLocalServer server() {
        return new RegisteredLocalServer(new BuildingId("building-1"), "http://localhost:8081", Instant.now(), true);
    }

    private List<UserSyncDto> users() {
        return Collections.singletonList(new UserSyncDto("u1", "user1", "hash", List.of("ROLE_USER")));
    }

    private ResponseEntity<List<UserSyncAckDto>> ackResponse() {
        return new ResponseEntity<>(List.of(new UserSyncAckDto("u1", true, null)), HttpStatus.OK);
    }

    @Test
    void retriesOn503ThenSucceeds() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"))
                .thenReturn(ackResponse());

        assertThatCode(() -> adapter.pushUsers(users(), server())).doesNotThrowAnyException();

        verify(mockRestTemplate, times(2)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void noRetryOn400NonTransient() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        assertThatThrownBy(() -> adapter.pushUsers(users(), server()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push users to local server");

        verify(mockRestTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void givesUpAfter3AttemptsOnContinued503() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"));

        assertThatThrownBy(() -> adapter.pushUsers(users(), server()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push users to local server");

        verify(mockRestTemplate, times(3)).exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    void returnsAckListFromResponseBody() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        LocalRestAdapter adapter = new LocalRestAdapter(mockRestTemplate, "test-api-key");

        when(mockRestTemplate.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ackResponse());

        List<UserSyncAckDto> acks = adapter.pushUsers(users(), server());

        assertThat(acks).hasSize(1);
        assertThat(acks.get(0).userId()).isEqualTo("u1");
        assertThat(acks.get(0).applied()).isTrue();
    }
}