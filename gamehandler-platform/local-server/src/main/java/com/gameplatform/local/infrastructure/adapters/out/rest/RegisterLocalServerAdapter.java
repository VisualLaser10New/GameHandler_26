package com.gameplatform.local.infrastructure.adapters.out.rest;

import com.gameplatform.local.domain.ports.out.RegisterLocalServerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.HttpURLConnection;
import java.util.Map;

/**
 * REST adapter that registers the local server against the central system by calling
 * {@code POST /internal/servers/register}.
 *
 * <p>Uses the same {@link SSLContext} and {@link RestTemplate} configuration as
 * {@link CentralSystemRestAdapter} (truststore-based trust of the central system's TLS cert).</p>
 */
@Component
public class RegisterLocalServerAdapter implements RegisterLocalServerPort {

    private static final Logger log = LoggerFactory.getLogger(RegisterLocalServerAdapter.class);

    private final RestTemplate restTemplate;
    private final String centralSystemUrl;
    private final String internalApiKey;
    private final String buildingId;
    private final String localBaseUrl;

    public RegisterLocalServerAdapter(
            SSLContext sslContext,
            @Value("${app.central-system-url}") String centralSystemUrl,
            @Value("${internal.api-key:secret}") String internalApiKey,
            @Value("${app.building-id}") String buildingId,
            @Value("${app.local-base-url}") String localBaseUrl) {
        this.centralSystemUrl = centralSystemUrl;
        this.internalApiKey = internalApiKey;
        this.buildingId = buildingId;
        this.localBaseUrl = localBaseUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                }
            }
        };
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public boolean register() {
        try {
            String url = centralSystemUrl + "/internal/servers/register";
            log.info("Registering local server [building={}] at central system: {}", buildingId, url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", internalApiKey);

            Map<String, String> body = Map.of(
                    "buildingId", buildingId,
                    "baseUrl", localBaseUrl
            );

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, requestEntity, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Local server registered successfully at central system");
                return true;
            }
            log.error("Failed to register local server. Status: {}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.warn("Registration attempt failed: {}", e.getMessage());
            return false;
        }
    }
}
