package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.QueryLocalServerUserCountPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
/**
 * M4 — central REST adapter that queries the local-server
 * {@code GET /internal/users/count} endpoint and returns the number of
 * {@code replicated_users} rows the server currently holds.
 *
 * <p>Wiring conventions are deliberately identical to {@link LocalServerRestAdapter}
 * so that both REST clients share the same api-key header
 * ({@code X-Internal-Api-Key}), the same {@link SimpleClientHttpRequestFactory}
 * timeouts ({@code central.replication.connect-timeout-ms} /
 * {@code central.replication.read-timeout-ms}, both default 5000 ms), and the
 * same {@link RetryTemplate} shape (3 attempts, exponential backoff 100 ms →
 * 10 s, retry on {@link TransientPushException} only). Transient classification
 * is delegated to {@link LocalServerRestAdapter#isTransient(Exception)} so the
 * policy stays in a single place (DRY).</p>
 *
 * <p><b>Failure contract (chosen over throwing):</b> on any exception or non-2xx
 * response after exhausting retries the adapter returns
 * {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE} ({@code -1L})
 * instead of throwing. The sentinel is declared on the port interface so the
 * application service can reference it without importing the adapter (hexagonal
 * dependency rule: app layer depends only on {@code domain/ports/out}). The
 * reconciliation service treats this as "unknown" and skips that server for
 * the current cycle — this is more conservative than throwing because a single
 * unreachable server would otherwise abort the entire sweep (or, if caught
 * per-server, would still log the same WARN). Returning {@code -1L} keeps the
 * adapter signature simple ({@code long} return type, no checked exception) and
 * lets the service decide policy.</p>
 */
@Component
public class LocalServerUserCountRestAdapter implements QueryLocalServerUserCountPort {

    private static final Logger log = LoggerFactory.getLogger(LocalServerUserCountRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalServerUserCountRestAdapter(
            @Value("${internal.api-key}") String apiKey,
            @Value("${central.replication.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${central.replication.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.apiKey = apiKey;
        this.retryTemplate = buildDefaultRetryTemplate();
    }

    // Package-private constructor for testing — mirrors LocalServerRestAdapter.
    LocalServerUserCountRestAdapter(RestTemplate restTemplate, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.retryTemplate = buildDefaultRetryTemplate();
    }

    private static RetryTemplate buildDefaultRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(100, 2.0, 10000)
                .retryOn(TransientPushException.class)
                .build();
    }

    @Override
    public long countReplicatedUsers(RegisteredLocalServer server) {
        String url = server.getBaseUrl() + "/internal/users/count";
        log.debug("Querying replicated_users count from local server at {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            return retryTemplate.execute(new org.springframework.retry.RetryCallback<Long, Exception>() {
                @Override
                public Long doWithRetry(RetryContext context) throws Exception {
                    try {
                        ResponseEntity<Long> response = restTemplate.exchange(
                                url, HttpMethod.GET, entity, Long.class);
                        Long body = response.getBody();
                        if (body == null) {
                            log.warn("Null body from count endpoint at {}", url);
                            return QueryLocalServerUserCountPort.COUNT_UNAVAILABLE;
                        }
                        return body;
                    } catch (Exception e) {
                        if (LocalServerRestAdapter.isTransient(e)) {
                            log.warn("Transient failure querying count from {} (attempt {}). Retrying...",
                                    url, context.getRetryCount() + 1, e);
                            throw new TransientPushException("Transient failure querying count from " + url, e);
                        } else {
                            log.error("Non-transient failure querying count from {}", url, e);
                            throw e;
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to query replicated_users count from {} after retries; returning {}",
                    url, QueryLocalServerUserCountPort.COUNT_UNAVAILABLE, e);
            return QueryLocalServerUserCountPort.COUNT_UNAVAILABLE;
        }
    }
}