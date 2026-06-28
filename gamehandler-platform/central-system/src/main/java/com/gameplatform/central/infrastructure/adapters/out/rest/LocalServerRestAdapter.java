package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class LocalServerRestAdapter implements PushUserToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalServerRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalServerRestAdapter(@Value("${internal.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        this.apiKey = apiKey;
    }

    // Package-private constructor for testing
    LocalServerRestAdapter(RestTemplate restTemplate, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Override
    public void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server) {
        String url = server.getBaseUrl() + "/internal/users/sync";
        log.info("Pushing {} users to local server at {}", users.size(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", apiKey);

        HttpEntity<List<UserSyncDto>> entity = new HttpEntity<>(users, headers);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
                log.info("Successfully pushed users to local server at {}", url);
                return;
            } catch (Exception e) {
                if (attempt < maxAttempts && isTransient(e)) {
                    long sleepMs = 100L * attempt;
                    log.warn("Transient failure pushing users to local server at {} (attempt {}/{}). Retrying in {}ms...", url, attempt, maxAttempts, sleepMs, e);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted while pushing users to {}", url, ie);
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("Failed to push users to local server at {} after {} attempts.", url, attempt, e);
                    throw new RuntimeException("Failed to push users to local server: " + url, e);
                }
            }
        }
    }

    private boolean isTransient(Exception e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof HttpStatusCodeException) {
            org.springframework.http.HttpStatusCode status = ((HttpStatusCodeException) e).getStatusCode();
            return status.is5xxServerError() || status.value() == 429 || status.value() == 408;
        }
        return false;
    }
}

