package com.gameplatform.local.infrastructure.adapters.out.rest;

import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.HttpURLConnection;

@Component
public class CentralSystemRestAdapter implements SyncCentralSystemPort {

    private static final Logger log = LoggerFactory.getLogger(CentralSystemRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String centralSystemUrl;
    private final String internalApiKey;

    public CentralSystemRestAdapter(
            SSLContext sslContext,
            @Value("${app.central-system-url}") String centralSystemUrl,
            @Value("${internal.api-key}") String internalApiKey) {
        this.centralSystemUrl = centralSystemUrl;
        this.internalApiKey = internalApiKey;

        // Configure RestTemplate to use local SSLContext for HttpsURLConnection
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
    public boolean isReachable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            // Query the sync endpoint using GET, which should respond with 405 Method Not Allowed (since GET is not supported),
            // meaning the server is reachable and active.
            restTemplate.exchange(centralSystemUrl + "/internal/sync/receive", HttpMethod.GET, entity, Void.class);
            return true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Server responded with an HTTP status, meaning it's alive and reachable
            return true;
        } catch (Exception e) {
            log.warn("Central system is unreachable at {}: {}", centralSystemUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendSyncPayload(SyncPayloadDto payload) {
        try {
            String url = centralSystemUrl + "/internal/sync/receive";
            log.info("Sending sync payload to central system at {} for building {}", url, payload.buildingId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", internalApiKey);

            HttpEntity<SyncPayloadDto> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, requestEntity, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Sync payload sent successfully to central system");
                return true;
            } else {
                log.error("Failed to send sync payload. Status code: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error occurred while sending sync payload to central system", e);
            return false;
        }
    }
}

