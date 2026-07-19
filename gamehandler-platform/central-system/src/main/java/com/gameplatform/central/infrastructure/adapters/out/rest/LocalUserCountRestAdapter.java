package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.HttpURLConnection;
/**
 * Adapter REST che interroga il local server tramite l'endpoint
 * {@code GET /internal/users/count} e restituisce il numero di righe
 * {@code replicated_users} attualmente presenti sul server.
 *
 * <p>La configurazione di wiring &egrave; deliberatamente identica a quella di
 * {@link LocalRestAdapter}: stesso header api-key ({@code X-Internal-Api-Key}),
 * stessi timeout {@link SimpleClientHttpRequestFactory}
 * ({@code central.replication.connect-timeout-ms} /
 * {@code central.replication.read-timeout-ms}, entrambi con default 5000 ms) e
 * stesso {@link RetryTemplate} (3 tentativi, backoff esponenziale 100 ms →
 * 10 s, retry solo su {@link TransientPushException}). La classificazione
 * degli errori transienti &egrave; delegata a
 * {@link LocalRestAdapter#isTransient(Exception)} per mantenere la politica
 * in un unico punto (DRY).</p>
 *
 * <p><b>Contratto di fallimento:</b> in caso di eccezione o risposta non-2xx
 * dopo aver esaurito i tentativi, l'adapter restituisce
 * {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE} ({@code -1L})
 * invece di lanciare un'eccezione. Il valore sentinella &egrave; dichiarato
 * sull'interfaccia del port in modo che il servizio applicativo possa
 * referenziarlo senza importare l'adapter (regola esagonale: il livello
 * applicativo dipende solo da {@code domain/ports/out}).</p>
 *
 * @see LocalRestAdapter
 * @see QueryLocalServerUserCountPort
 */
@Component
public class LocalUserCountRestAdapter implements QueryLocalServerUserCountPort {

    private static final Logger log = LoggerFactory.getLogger(LocalUserCountRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    /**
     * Costruisce l'adapter configurando il client HTTP con il contesto SSL, la
     * chiave API interna e i timeout di connessione e lettura.
     *
     * @param sslContext       contesto SSL utilizzato per le connessioni HTTPS; non deve essere {@code null}
     * @param apiKey           chiave API interna inviata nell'header {@code X-Internal-Api-Key}; non deve essere {@code null}
     * @param connectTimeoutMs timeout di connessione in millisecondi; se non configurato assume il valore predefinito 5000
     * @param readTimeoutMs    timeout di lettura in millisecondi; se non configurato assume il valore predefinito 5000
     */
    @org.springframework.beans.factory.annotation.Autowired
    public LocalUserCountRestAdapter(
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

    /**
     * Costruisce l'adapter con un {@link RestTemplate} fornito esternamente,
     * utile per i test unitari.
     *
     * @param restTemplate il template REST da utilizzare per le chiamate HTTP; non deve essere {@code null}
     * @param apiKey       chiave API interna inviata nell'header {@code X-Internal-Api-Key}; non deve essere {@code null}
     */
    LocalUserCountRestAdapter(RestTemplate restTemplate, String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.retryTemplate = buildDefaultRetryTemplate();
    }

    /**
     * Costruisce il template di retry predefinito con 3 tentativi, backoff
     * esponenziale da 100 ms a 10 s e ripetizione solo su
     * {@link TransientPushException}.
     *
     * @return il template di retry configurato; mai {@code null}
     */
    private static RetryTemplate buildDefaultRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(100, 2.0, 10000)
                .retryOn(TransientPushException.class)
                .build();
    }

    /**
     * Restituisce il numero di utenti replicati ({@code replicated_users})
     * presenti sul local server specificato, interrogando l'endpoint
     * {@code GET /internal/users/count}.
     * <p>
     * In caso di errore transiente (dopo aver esaurito i tentativi di retry) o
     * di qualsiasi altra eccezione, restituisce
     * {@link QueryLocalServerUserCountPort#COUNT_UNAVAILABLE} ({@code -1L})
     * senza lanciare eccezioni.
     *
     * @param server il local server da interrogare; non deve essere {@code null}
     * @return il numero di utenti replicati, oppure {@code -1L} se il conteggio
     *         non è disponibile
     * @see QueryLocalServerUserCountPort#COUNT_UNAVAILABLE
     * @see LocalRestAdapter#isTransient(Exception)
     */
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
                        if (LocalRestAdapter.isTransient(e)) {
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