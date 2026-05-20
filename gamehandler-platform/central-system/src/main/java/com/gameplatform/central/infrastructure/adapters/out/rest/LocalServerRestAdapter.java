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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class LocalServerRestAdapter implements PushUserToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalServerRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;

    public LocalServerRestAdapter(@Value("${internal.api-key}") String apiKey) {
        this.restTemplate = new RestTemplate();
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

        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("Successfully pushed users to local server at {}", url);
        } catch (Exception e) {
            log.error("Failed to push users to local server at {}: {}", url, e.getMessage());
            throw new RuntimeException("Failed to push users to local server: " + url, e);
        }
    }
}

