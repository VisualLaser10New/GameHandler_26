package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushUserToLocalServersPort;
import com.gameplatform.shared.dto.UserSyncAckDto;
import com.gameplatform.shared.dto.UserSyncDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Adapter REST che replica gli utenti verso un singolo local server tramite il
 * relativo endpoint {@code PUT /internal/users/sync}.
 *
 * <p>Configura un {@link RestTemplate} con {@link SSLContext} dedicato, timeout
 * di connessione e lettura, header {@code X-Internal-Api-Key} e un
 * {@link RetryTemplate} a 3 tentativi. A differenza degli altri adapter di
 * replica, ritorna gli ack per utente ricevuti dal local server.</p>
 *
 * @see PushUserToLocalServersPort
 */
@Component
public class LocalRestAdapter implements PushUserToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    /**
     * Costruisce l'adapter configurando il client HTTP con il contesto SSL, la
     * chiave API interna e i timeout di connessione e lettura.
     *
     * @param sslContext        contesto SSL utilizzato per le connessioni HTTPS; non deve essere {@code null}
     * @param apiKey            chiave API interna inviata nell'header {@code X-Internal-Api-Key}; non deve essere {@code null}
     * @param connectTimeoutMs  timeout di connessione in millisecondi; se non configurato assume il valore predefinito 5000
     * @param readTimeoutMs     timeout di lettura in millisecondi; se non configurato assume il valore predefinito 5000
     */
    @org.springframework.beans.factory.annotation.Autowired
    public LocalRestAdapter(
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
    LocalRestAdapter(RestTemplate restTemplate, String apiKey) {
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
     * Invia una lista di utenti al local server specificato tramite una
     * richiesta {@code PUT} all'endpoint {@code /internal/users/sync} e
     * restituisce gli ack ricevuti per ogni utente.
     * <p>
     * In caso di errore transiente dopo aver esaurito i tentativi di retry,
     * rilancia un'eccezione {@link RuntimeException} contenente la causa
     * originale.
     *
     * @param users  la lista degli utenti da sincronizzare; non deve essere {@code null}
     * @param server il local server di destinazione; non deve essere {@code null}
     * @return la lista degli ack ricevuti dal local server per ogni utente;
     *         una lista vuota se il corpo della risposta &egrave; {@code null}
     * @throws RuntimeException se la richiesta fallisce dopo aver esaurito tutti i tentativi di retry
     * @see #isTransient(Exception)
     * @see #isConnectionRefusedRoot(Throwable)
     */
    @Override
    public List<UserSyncAckDto> pushUsers(List<UserSyncDto> users, RegisteredLocalServer server) {
        String url = server.getBaseUrl() + "/internal/users/sync";
        log.info("Pushing {} users to local server at {}", users.size(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", apiKey);

        HttpEntity<List<UserSyncDto>> entity = new HttpEntity<>(users, headers);

        try {
            return retryTemplate.execute(new org.springframework.retry.RetryCallback<List<UserSyncAckDto>, Exception>() {
                @Override
                public List<UserSyncAckDto> doWithRetry(RetryContext context) throws Exception {
                    try {
                        ResponseEntity<List<UserSyncAckDto>> response = restTemplate.exchange(
                                url, HttpMethod.PUT, entity, new ParameterizedTypeReference<List<UserSyncAckDto>>() {});
                        List<UserSyncAckDto> acks = response.getBody();
                        log.info("Successfully pushed users to local server at {}", url);
                        return acks == null ? List.of() : acks;
                    } catch (Exception e) {
                        if (isTransient(e)) {
                            log.warn("Transient failure pushing users to local server at {} (attempt {}). Retrying...",
                                    url, context.getRetryCount() + 1, e);
                            throw new TransientPushException("Transient failure pushing users to " + url, e);
                        } else {
                            log.error("Non-transient failure pushing users to local server at {}", url, e);
                            throw e;
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (isConnectionRefusedRoot(e)) {
                log.warn("Local server at {} unreachable — event will be retried; server marked inactive if persistent.", url);
            } else {
                log.error("Failed to push users to local server at {} after retries.", url, e);
            }
            throw new RuntimeException("Failed to push users to local server: " + url, e);
        }
    }

    /**
     * Verifica se un'eccezione rappresenta un errore transiente che pu&ograve;
     * essere ritentato.
     * <p>
     * Sono considerati transienti: {@link ResourceAccessException}, errori HTTP
     * 5xx, {@code 429 Too Many Requests} e {@code 408 Request Timeout}.
     *
     * @param e l'eccezione da classificare; non deve essere {@code null}
     * @return {@code true} se l'eccezione &egrave; di tipo transiente,
     *         {@code false} altrimenti
     */
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

    /**
     * Verifica se la causa radice di un'eccezione (o dell'intera catena di
     * cause) &egrave; un {@link java.net.ConnectException}, indicando che il
     * server di destinazione non &egrave; raggiungibile.
     *
     * @param t l'eccezione o throwable da ispezionare; non deve essere {@code null}
     * @return {@code true} se nella catena delle cause &egrave; presente un
     *         {@code ConnectException}, {@code false} altrimenti
     */
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
