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

/**
 * Adattatore REST per la comunicazione con il sistema centrale.
 * <p>
 * Implementa {@link SyncCentralSystemPort} per verificare la raggiungibilità del sistema centrale
 * e inviare i payload di sincronizzazione tramite chiamate HTTP. Configura un {@link RestTemplate}
 * con un {@link SSLContext} personalizzato per la gestione delle connessioni HTTPS.
 * </p>
 *
 * @see SyncCentralSystemPort
 * @see RegisterLocalServerAdapter
 */
@Component
public class CentralSystemRestAdapter implements SyncCentralSystemPort {

    private static final Logger log = LoggerFactory.getLogger(CentralSystemRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String centralSystemUrl;
    private final String internalApiKey;

    /**
     * Costruisce un nuovo adattatore con il contesto SSL e le configurazioni specificate.
     *
     * @param sslContext       contesto SSL per le connessioni HTTPS verso il sistema centrale
     * @param centralSystemUrl URL di base del sistema centrale
     * @param internalApiKey   chiave API interna per l'autenticazione delle richieste
     */
    public CentralSystemRestAdapter(
            SSLContext sslContext,
            @Value("${app.central-system-url}") String centralSystemUrl,
            @Value("${internal.api-key:secret}") String internalApiKey) {
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

    /**
     * Verifica se il sistema centrale è raggiungibile effettuando una richiesta GET
     * all'endpoint di sincronizzazione.
     * <p>
     * Il metodo considera il sistema centrale raggiungibile se la risposta è un errore HTTP
     * gestito (es. 405 Method Not Allowed) o se la richiesta ha successo.
     * </p>
     *
     * @return {@code true} se il sistema centrale è raggiungibile, {@code false} altrimenti
     */
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

    /**
     * Invia un payload di sincronizzazione al sistema centrale tramite POST all'endpoint
     * di sincronizzazione.
     *
     * @param payload il DTO contenente i dati di sincronizzazione da inviare
     * @return {@code true} se l'invio è avvenuto con successo (risposta 2xx), {@code false} altrimenti
     */
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

