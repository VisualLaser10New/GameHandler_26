package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushMetadataToLocalServersPort;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Pushes LOCAL_ADMIN&harr;building metadata events to a single local server's
 * {@code PUT /internal/metadata/sync} endpoint.
 *
 * <p>Structural twin of {@link LocalRestAdapter} (same SSLContext wiring,
 * timeouts, {@code X-Internal-Api-Key} header and {@link RetryTemplate}) but
 * for the metadata replication flow. Returns {@code void}: there is no ack /
 * poison-isolation contract because the local upsert/delete is idempotent by
 * composite PK, so a transient transport failure is simply retried via the
 * outbox on the next scheduler tick.</p>
 */
@Component
public class LocalMetadataRestAdapter implements PushMetadataToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalMetadataRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalMetadataRestAdapter(
            SSLContext sslContext,
            @Value("${internal.api-key}") String apiKey,
            @Value("${central.replication.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${central.replication.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                }
            }
        };
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.apiKey = apiKey;
        this.retryTemplate = buildDefaultRetryTemplate();
    }

    // Package-private constructor for testing
    LocalMetadataRestAdapter(RestTemplate restTemplate, String apiKey) {
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
    public void pushMetadata(List<LocalAdminBuildingEventDto> events, RegisteredLocalServer server) {
        String url = server.getBaseUrl() + "/internal/metadata/sync";
        log.info("Pushing {} metadata events to local server at {}", events.size(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", apiKey);

        HttpEntity<List<LocalAdminBuildingEventDto>> entity = new HttpEntity<>(events, headers);

        try {
            retryTemplate.execute(new org.springframework.retry.RetryCallback<Void, Exception>() {
                @Override
                public Void doWithRetry(RetryContext context) throws Exception {
                    try {
                        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
                        log.info("Successfully pushed metadata events to local server at {}", url);
                        return null;
                    } catch (Exception e) {
                        if (isTransient(e)) {
                            log.warn("Transient failure pushing metadata to local server at {} (attempt {}). Retrying...",
                                    url, context.getRetryCount() + 1, e);
                            throw new TransientPushException("Transient failure pushing metadata to " + url, e);
                        } else {
                            log.error("Non-transient failure pushing metadata to local server at {}", url, e);
                            throw e;
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (isConnectionRefusedRoot(e)) {
                log.warn("Local server at {} unreachable — event will be retried; server marked inactive if persistent.", url);
            } else {
                log.error("Failed to push metadata to local server at {} after retries.", url, e);
            }
            throw new RuntimeException("Failed to push metadata to local server: " + url, e);
        }
    }

    static boolean isTransient(Exception e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof HttpStatusCodeException) {
            org.springframework.http.HttpStatusCode status = ((HttpStatusCodeException) e).getStatusCode();
            return status.is5xxServerError() || status.value() == 429 || status.value() == 408;
        }
        return false;
    }

    static boolean isConnectionRefusedRoot(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.net.ConnectException) {
                return true;
            }
            Throwable next = cur.getCause();
            if (next == cur) {
                break;
            }
            cur = next;
        }
        return false;
    }
}