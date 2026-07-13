package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushTournamentStandingsToLocalServersPort;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
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
 * Pushes {@code TOURNAMENT_STANDINGS_UPSERTED} events to a single local server's
 * {@code PUT /internal/tournaments/standings/sync} endpoint. Structural twin of
 * {@link LocalTournamentSummaryRestAdapter} (same SSLContext wiring, timeouts,
 * {@code X-Internal-Api-Key} header, {@link RetryTemplate} 3 attempts).
 *
 * <p>The request body is {@code List<TournamentStandingsEventDto>}; the receiver
 * upsert is a delete+insert snapshot idempotent by {@code tournamentId}, so a
 * transient transport failure is simply retried via the outbox on the next
 * scheduler tick.</p>
 *
 * <p>The local {@code InternalApiKeyFilter} already protects every
 * {@code /internal/**} path, so this endpoint is automatically secured by the
 * {@code X-Internal-Api-Key} header sent below.</p>
 */
@Component
public class LocalTournamentStandingsRestAdapter implements PushTournamentStandingsToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalTournamentStandingsRestAdapter.class);

    private static final String ENDPOINT_PATH = "/internal/tournaments/standings/sync";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalTournamentStandingsRestAdapter(
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

    LocalTournamentStandingsRestAdapter(RestTemplate restTemplate, String apiKey) {
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
    public void push(List<TournamentStandingsEventDto> events, RegisteredLocalServer server) {
        String url = server.getBaseUrl() + ENDPOINT_PATH;
        log.info("Pushing {} tournament-standings events to local server at {}", events.size(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", apiKey);

        HttpEntity<List<TournamentStandingsEventDto>> entity = new HttpEntity<>(events, headers);

        try {
            retryTemplate.execute(new org.springframework.retry.RetryCallback<Void, Exception>() {
                @Override
                public Void doWithRetry(RetryContext context) throws Exception {
                    try {
                        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
                        log.info("Successfully pushed tournament-standings events to local server at {}", url);
                        return null;
                    } catch (Exception e) {
                        if (isTransient(e)) {
                            log.warn("Transient failure pushing tournament-standings to local server at {} (attempt {}). Retrying...",
                                    url, context.getRetryCount() + 1, e);
                            throw new TransientPushException("Transient failure pushing tournament-standings to " + url, e);
                        } else {
                            log.error("Non-transient failure pushing tournament-standings to local server at {}", url, e);
                            throw e;
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (isConnectionRefusedRoot(e)) {
                log.warn("Local server at {} unreachable — event will be retried; server marked inactive if persistent.", url);
            } else {
                log.error("Failed to push tournament-standings to local server at {} after retries.", url, e);
            }
            throw new RuntimeException("Failed to push tournament-standings to local server: " + url, e);
        }
    }

    static boolean isTransient(Exception e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof HttpStatusCodeException) {
            HttpStatusCode status = ((HttpStatusCodeException) e).getStatusCode();
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