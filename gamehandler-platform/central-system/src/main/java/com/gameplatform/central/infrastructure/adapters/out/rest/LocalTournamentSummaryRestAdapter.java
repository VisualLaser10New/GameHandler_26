package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushTournamentSummaryToLocalServersPort;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
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
 * Adapter REST che invia eventi {@code TOURNAMENT_SUMMARY_UPSERTED} al local
 * server tramite l'endpoint {@code PUT /internal/tournaments/summaries/sync}.
 *
 * <p>Gemello strutturale di {@link LocalTournamentMatchRestAdapter} (stesso
 * wiring SSLContext, timeout, header {@code X-Internal-Api-Key} e
 * {@link RetryTemplate} a 3 tentativi). Il corpo della richiesta &egrave;
 * {@code List<TournamentSummaryEventDto>}; l'upsert lato ricevente &egrave;
 * idempotente per PK ({@code tournamentId}), quindi un errore di trasporto
 * transiente viene semplicemente ritentato tramite outbox al successivo ciclo
 * dello scheduler. Gli eventi {@code deleted=true} sono gestiti dal local
 * server come {@code deleteById}.</p>
 *
 * @see LocalTournamentMatchRestAdapter
 * @see PushTournamentSummaryToLocalServersPort
 */
@Component
public class LocalTournamentSummaryRestAdapter implements PushTournamentSummaryToLocalServersPort {

    private static final Logger log = LoggerFactory.getLogger(LocalTournamentSummaryRestAdapter.class);

    private static final String ENDPOINT_PATH = "/internal/tournaments/summaries/sync";

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
    public LocalTournamentSummaryRestAdapter(
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
    LocalTournamentSummaryRestAdapter(RestTemplate restTemplate, String apiKey) {
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
     * Invia una lista di eventi di riepilogo torneo al local server specificato
     * tramite una richiesta {@code PUT} all'endpoint di sincronizzazione.
     * <p>
     * In caso di errore transiente dopo aver esaurito i tentativi di retry,
     * rilancia un'eccezione {@link RuntimeException} contenente la causa
     * originale.
     *
     * @param events la lista degli eventi di riepilogo torneo da inviare; non deve essere {@code null}
     * @param server il local server di destinazione; non deve essere {@code null}
     * @throws RuntimeException se la richiesta fallisce dopo aver esaurito tutti i tentativi di retry
     * @see #isTransient(Exception)
     * @see #isConnectionRefusedRoot(Throwable)
     */
    @Override
    public void push(List<TournamentSummaryEventDto> events, RegisteredLocalServer server) {
        String url = server.getBaseUrl() + ENDPOINT_PATH;
        log.info("Pushing {} tournament-summary events to local server at {}", events.size(), url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", apiKey);

        HttpEntity<List<TournamentSummaryEventDto>> entity = new HttpEntity<>(events, headers);

        try {
            retryTemplate.execute(new org.springframework.retry.RetryCallback<Void, Exception>() {
                @Override
                public Void doWithRetry(RetryContext context) throws Exception {
                    try {
                        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
                        log.info("Successfully pushed tournament-summary events to local server at {}", url);
                        return null;
                    } catch (Exception e) {
                        if (isTransient(e)) {
                            log.warn("Transient failure pushing tournament-summaries to local server at {} (attempt {}). Retrying...",
                                    url, context.getRetryCount() + 1, e);
                            throw new TransientPushException("Transient failure pushing tournament-summaries to " + url, e);
                        } else {
                            log.error("Non-transient failure pushing tournament-summaries to local server at {}", url, e);
                            throw e;
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (isConnectionRefusedRoot(e)) {
                log.warn("Local server at {} unreachable — event will be retried; server marked inactive if persistent.", url);
            } else {
                log.error("Failed to push tournament-summaries to local server at {} after retries.", url, e);
            }
            throw new RuntimeException("Failed to push tournament-summaries to local server: " + url, e);
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
            HttpStatusCode status = ((HttpStatusCodeException) e).getStatusCode();
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