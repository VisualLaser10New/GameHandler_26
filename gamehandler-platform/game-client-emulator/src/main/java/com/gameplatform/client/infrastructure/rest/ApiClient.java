package com.gameplatform.client.infrastructure.rest;

import com.gameplatform.client.domain.exception.AuthenticationException;
import com.gameplatform.client.domain.exception.AuthorizationException;
import com.gameplatform.client.domain.exception.HttpClientResponseException;
import com.gameplatform.client.domain.exception.ServerUnavailableException;
import com.gameplatform.client.infrastructure.security.HttpClientHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Adattatore REST centralizzato che espone metodi asincroni tipizzati
 * per le operazioni HTTP GET, POST, PUT, PATCH e DELETE verso il Local Server.
 * <p>
 * Utilizza un singleton {@link HttpClient} configurato con il truststore
 * di progetto tramite {@link HttpClientHelper#getHttpClient(String)}.
 * L'URL di base viene risolto dalla variabile d'ambiente {@code LOCAL_SERVER_URL}
 * con default {@code https://localhost:8181}.
 * <p>
 * Ogni metodo gestisce automaticamente:
 * <ul>
 *   <li>l'intestazione {@code Authorization: Bearer} quando il token è presente;</li>
 *   <li>la serializzazione JSON del corpo richiesta tramite {@link ObjectMappers#SHARED};</li>
 *   <li>la mappatura degli errori HTTP in eccezioni di dominio
 *       (401 &rarr; {@link AuthenticationException}, 403 &rarr; {@link AuthorizationException},
 *       5xx/timeout/rifiuto connessione &rarr; {@link ServerUnavailableException},
 *       altri 4xx &rarr; {@link HttpClientResponseException});</li>
 *   <li>la deserializzazione della risposta nel tipo richiesto.</li>
 * </ul>
 * <p>
 * Le risposte di tipo generico (liste) richiedono l'overload con
 * {@link TypeReference} a causa dell'erasione dei tipi in Java.
 * Tutti i metodi restituiscono {@link CompletableFuture}.
 */
public class ApiClient {

    /**
     * URL di base predefinito del Local Server.
     * Sovrascrivibile tramite la variabile d'ambiente {@code LOCAL_SERVER_URL}.
     */
    public static final String DEFAULT_BASE_URL = "https://localhost:8181";

    /**
     * Mappa statica che associa ogni identificativo di building all'URL
     * del rispettivo Local Server in ambiente di sviluppo.
     * <p>
     * La topologia docker-compose di sviluppo espone ogni Local Server
     * su una porta locale stabile, consentendo al selettore building
     * di PLATFORM_ADMIN di cambiare l'URL di base di {@link ApiClient}
     * senza risolvere nomi DNS di servizio.
     */
    public static final java.util.Map<String, String> BUILDING_URLS = java.util.Map.of(
            "building-1", "https://localhost:8181",
            "building-2", "https://localhost:8182",
            "building-3", "https://localhost:8183"
    );

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private volatile String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    /**
     * Costruisce un nuovo {@code ApiClient} risolvendo l'URL di base
     * dalla variabile d'ambiente {@code LOCAL_SERVER_URL}.
     * <p>
     * Se la variabile d'ambiente non è impostata, utilizza
     * {@link #DEFAULT_BASE_URL}. L'accesso canonico è tramite
     * {@link #instance()}.
     */
    public ApiClient() {
        this(System.getenv().getOrDefault("LOCAL_SERVER_URL", DEFAULT_BASE_URL));
    }

    /**
     * Costruisce un nuovo {@code ApiClient} con l'URL di base specificato.
     *
     * @param baseUrl l'URL di base del Local Server; se {@code null}
     *                o vuoto, viene utilizzato {@link #DEFAULT_BASE_URL}
     */
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        this.httpClient = HttpClientHelper.getHttpClient(this.baseUrl);
        this.mapper = ObjectMappers.SHARED;
    }

    /**
     * Modifica dinamicamente l'URL di base del client verso un diverso
     * Local Server, utilizzato dal selettore building di PLATFORM_ADMIN.
     * <p>
     * La sostituzione aggiorna solo l'host e la porta delle richieste
     * successive; l'{@link HttpClient} sottostante viene riutilizzato
     * poiché tutti i Local Server condividono lo stesso certificato TLS.
     *
     * @param baseUrl il nuovo URL di base (es. {@code https://localhost:8182});
     *                un valore {@code null} o vuoto viene ignorato
     */
    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        this.baseUrl = baseUrl;
    }

    /**
     * Restituisce l'URL di base correntemente configurato.
     *
     * @return l'URL di base del Local Server, mai {@code null}
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    // ───────────────────────────── GET ─────────────────────────────

    /**
     * Esegue una richiesta HTTP GET asincrona verso il percorso specificato
     * e deserializza la risposta in un oggetto del tipo indicato.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base (es. {@code /api/tournaments})
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> get(String path, Class<T> responseType) {
        return sendAsync(buildGET(path), responseType, null);
    }

    /**
     * Esegue una richiesta HTTP GET asincrona verso il percorso specificato
     * e deserializza la risposta in un tipo generico (es. {@code List<T}).
     *
     * @param <T>     il tipo generico restituito
     * @param path    il percorso relativo all'URL di base
     * @param typeRef il riferimento di tipo per la deserializzazione generica
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> get(String path, TypeReference<T> typeRef) {
        return sendAsync(buildGET(path), null, typeRef);
    }

    /**
     * Esegue una richiesta HTTP GET asincrona con parametri di query
     * e deserializza la risposta in un oggetto del tipo indicato.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base
     * @param query        la stringa di query da aggiungere al percorso;
     *                     può iniziare con '?' oppure essere una stringa nuda;
     *                     se {@code null} o vuota non viene aggiunta alcuna query
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> get(String path, String query, Class<T> responseType) {
        return sendAsync(buildGET(appendQuery(path, query)), responseType, null);
    }

    /**
     * Esegue una richiesta HTTP GET asincrona con parametri di query
     * e deserializza la risposta in un tipo generico.
     *
     * @param <T>     il tipo generico restituito
     * @param path    il percorso relativo all'URL di base
     * @param query   la stringa di query da aggiungere al percorso;
     *                se {@code null} o vuota non viene aggiunta alcuna query
     * @param typeRef il riferimento di tipo per la deserializzazione generica
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> get(String path, String query, TypeReference<T> typeRef) {
        return sendAsync(buildGET(appendQuery(path, query)), null, typeRef);
    }

    // ───────────────────────────── POST ────────────────────────────

    /**
     * Esegue una richiesta HTTP POST asincrona con corpo JSON
     * e deserializza la risposta in un oggetto del tipo indicato.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base
     * @param body         l'oggetto da serializzare come corpo JSON della richiesta;
     *                     se {@code null} viene inviato un corpo vuoto
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
        return sendAsync(buildPOST(path, body), responseType, null);
    }

    /**
     * Esegue una richiesta HTTP POST asincrona con corpo JSON
     * e deserializza la risposta in un tipo generico.
     *
     * @param <T>     il tipo generico restituito
     * @param path    il percorso relativo all'URL di base
     * @param body    l'oggetto da serializzare come corpo JSON della richiesta;
     *                se {@code null} viene inviato un corpo vuoto
     * @param typeRef il riferimento di tipo per la deserializzazione generica
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> post(String path, Object body, TypeReference<T> typeRef) {
        return sendAsync(buildPOST(path, body), null, typeRef);
    }

    /**
     * Esegue una richiesta HTTP POST asincrona senza corpo
     * e deserializza la risposta in un oggetto del tipo indicato.
     * <p>
     * Utilizzato per azioni lifecycle come {@code /open}.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> postEmpty(String path, Class<T> responseType) {
        return sendAsync(buildPOST(path, null), responseType, null);
    }

    // ───────────────────────────── PUT ─────────────────────────────

    /**
     * Esegue una richiesta HTTP PUT asincrona con corpo JSON
     * e deserializza la risposta in un oggetto del tipo indicato.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base
     * @param body         l'oggetto da serializzare come corpo JSON della richiesta;
     *                     se {@code null} viene inviato un corpo vuoto
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> put(String path, Object body, Class<T> responseType) {
        return sendAsync(buildPUT(path, body), responseType, null);
    }

    /**
     * Esegue una richiesta HTTP PATCH asincrona con corpo JSON
     * e deserializza la risposta in un oggetto del tipo indicato.
     *
     * @param <T>          il tipo dell'oggetto restituito
     * @param path         il percorso relativo all'URL di base
     * @param body         l'oggetto da serializzare come corpo JSON della richiesta;
     *                     se {@code null} viene inviato un corpo vuoto
     * @param responseType la classe del tipo atteso per la deserializzazione
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    public <T> CompletableFuture<T> patch(String path, Object body, Class<T> responseType) {
        return sendAsync(buildRequest(path, "PATCH", serialize(body)), responseType, null);
    }

    // ───────────────────────────── DELETE ──────────────────────────

    /**
     * Esegue una richiesta HTTP DELETE asincrona verso il percorso specificato.
     * <p>
     * A differenza degli altri metodi, non deserializza il corpo della risposta.
     *
     * @param path il percorso relativo all'URL di base
     * @return un {@link CompletableFuture} che restituisce {@code null} al completamento con successo
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx, timeout o connessione rifiutata
     * @throws RuntimeException            se il server risponde con altri codici 4xx
     */
    public CompletableFuture<Void> delete(String path) {
        HttpRequest request = buildRequest(path, "DELETE", null);
        return wrapTransport(httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    int code = response.statusCode();
                    if (code == 401) throw new AuthenticationException("Authentication required (401)");
                    if (code == 403) throw new AuthorizationException("Access denied (403)");
                    if (code >= 500) throw new ServerUnavailableException("Server error " + code);
                    if (code >= 400) throw new RuntimeException("Delete failed: HTTP " + code);
                    return null;
                }));
    }

    // ───────────────────────────── request builders ────────────────

    /**
     * Costruisce una richiesta HTTP GET per il percorso specificato.
     *
     * @param fullPath il percorso completo (URL di base + percorso relativo)
     * @return la richiesta HTTP configurata
     */
    private HttpRequest buildGET(String fullPath) {
        return buildRequest(fullPath, "GET", null);
    }

    /**
     * Costruisce una richiesta HTTP POST con corpo JSON serializzato.
     *
     * @param path il percorso relativo all'URL di base
     * @param body l'oggetto da serializzare come corpo; se {@code null} viene inviato un corpo vuoto
     * @return la richiesta HTTP configurata
     */
    private HttpRequest buildPOST(String path, Object body) {
        return buildRequest(path, "POST", serialize(body));
    }

    /**
     * Costruisce una richiesta HTTP PUT con corpo JSON serializzato.
     *
     * @param path il percorso relativo all'URL di base
     * @param body l'oggetto da serializzare come corpo; se {@code null} viene inviato un corpo vuoto
     * @return la richiesta HTTP configurata
     */
    private HttpRequest buildPUT(String path, Object body) {
        return buildRequest(path, "PUT", serialize(body));
    }

    /**
     * Costruisce una richiesta HTTP generica con metodo, intestazioni e corpo specificati.
     * <p>
     * Aggiunge automaticamente l'intestazione {@code Accept: application/json},
     * l'intestazione {@code Authorization: Bearer} se il token è presente
     * in {@link HttpClientHelper}, e l'intestazione {@code Content-Type: application/json}
     * se il corpo non è {@code null}.
     *
     * @param fullPath il percorso completo (URL di base + percorso relativo)
     * @param method   il metodo HTTP (GET, POST, PUT, DELETE, PATCH)
     * @param body     il corpo della richiesta come stringa JSON; {@code null} per richieste senza corpo
     * @return la richiesta HTTP configurata
     */
    private HttpRequest buildRequest(String fullPath, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + fullPath))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        String token = HttpClientHelper.getToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    // ───────────────────────────── send + deserialise ──────────────

    /**
     * Invia la richiesta HTTP in modo asincrono e deserializza la risposta.
     * <p>
     * Gestisce la mappatura dei codici di stato HTTP nelle eccezioni di dominio:
     * 401 &rarr; {@link AuthenticationException}, 403 &rarr; {@link AuthorizationException},
     * 5xx &rarr; {@link ServerUnavailableException}, altri 4xx &rarr; {@link HttpClientResponseException}.
     * Per risposte 204 o corpo vuoto restituisce {@code null}.
     *
     * @param <T>        il tipo dell'oggetto restituito
     * @param request    la richiesta HTTP da inviare
     * @param singleType la classe del tipo atteso per la deserializzazione; utilizzata se {@code listType} è {@code null}
     * @param listType   il riferimento di tipo generico per la deserializzazione; se diverso da {@code null} prevale su {@code singleType}
     * @return un {@link CompletableFuture} che restituisce l'oggetto deserializzato o {@code null} per risposte 204/corpo vuoto
     * @throws AuthenticationException     se il server risponde con 401
     * @throws AuthorizationException      se il server risponde con 403
     * @throws ServerUnavailableException  se il server risponde con 5xx
     * @throws HttpClientResponseException se il server risponde con altri codici 4xx
     */
    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> singleType, TypeReference<T> listType) {
        return wrapTransport(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int code = response.statusCode();
                    if (code == 401) {
                        throw new AuthenticationException("Authentication required (401)");
                    }
                    if (code == 403) {
                        throw new AuthorizationException("Access denied (403)");
                    }
                    if (code >= 500) {
                        String body = response.body() == null ? "" : truncate(response.body(), 200);
                        throw new ServerUnavailableException("Server error " + code + " — body=" + body);
                    }
                    if (code >= 400) {
                        String body = response.body() == null ? "" : truncate(response.body(), 200);
                        throw new HttpClientResponseException(code, body);
                    }
                    if (code == 204 || response.body() == null || response.body().isBlank()) {
                        return null;
                    }
                    return deserialize(response.body(), singleType, listType);
                }));
    }

    /**
     * Avvolge il {@link CompletableFuture} fornito intercettando le eccezioni
     * di trasporto (timeout, connessione rifiutata, {@link IOException}) e
     * convertendole in {@link ServerUnavailableException}.
     * <p>
     * Le eccezioni di dominio ({@link AuthenticationException},
     * {@link AuthorizationException}, {@link ServerUnavailableException})
     * già sollevate per i codici 401/403/5xx vengono rilanciate invariate.
     *
     * @param <T>    il tipo del risultato del future
     * @param future il {@link CompletableFuture} da avvolgere
     * @return un {@link CompletableFuture} con le eccezioni di trasporto convertite
     */
    private <T> CompletableFuture<T> wrapTransport(CompletableFuture<T> future) {
        return future.exceptionally(ex -> {
            Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                    ? ex.getCause() : ex;
            if (cause instanceof ServerUnavailableException
                    || cause instanceof AuthenticationException
                    || cause instanceof AuthorizationException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof HttpTimeoutException) {
                throw new ServerUnavailableException("Local Server unreachable: request timed out", cause);
            }
            if (cause instanceof ConnectException) {
                throw new ServerUnavailableException("Local Server unreachable: connection refused", cause);
            }
            if (cause instanceof IOException) {
                throw new ServerUnavailableException("Local Server unreachable: " + cause.getMessage(), cause);
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        });
    }

    /**
     * Deserializza il corpo della risposta JSON nell'oggetto del tipo specificato.
     * <p>
     * Se {@code listType} non è {@code null} utilizza quello per la deserializzazione
     * di tipi generici; altrimenti utilizza {@code singleType}. Se il tipo richiesto
     * è {@link Void} restituisce {@code null}.
     *
     * @param <T>        il tipo dell'oggetto deserializzato
     * @param body       il corpo della risposta JSON; non {@code null}
     * @param singleType la classe del tipo atteso; utilizzata se {@code listType} è {@code null}
     * @param listType   il riferimento di tipo generico; se diverso da {@code null} prevale su {@code singleType}
     * @return l'oggetto deserializzato, o {@code null} se il tipo è {@link Void}
     * @throws RuntimeException se la deserializzazione fallisce
     */
    @SuppressWarnings("unchecked")
    private <T> T deserialize(String body, Class<T> singleType, TypeReference<T> listType) {
        try {
            if (listType != null) {
                return mapper.readValue(body, listType);
            }
            if (singleType == Void.class || singleType == void.class) {
                return null;
            }
            return mapper.readValue(body, singleType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response: " + e.getMessage()
                    + " — body=" + truncate(body, 200), e);
        }
    }

    /**
     * Serializza un oggetto in una stringa JSON.
     * <p>
     * Se l'oggetto è già una stringa, la restituisce direttamente.
     * Se l'oggetto è {@code null}, restituisce {@code null}.
     *
     * @param body l'oggetto da serializzare; può essere {@code null}
     * @return la rappresentazione JSON dell'oggetto, o {@code null} se l'input è {@code null}
     * @throws RuntimeException se la serializzazione fallisce
     */
    private String serialize(Object body) {
        if (body == null) return null;
        if (body instanceof String s) return s;
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body: " + e.getMessage(), e);
        }
    }

    /**
     * Concatena una stringa di query al percorso specificato.
     * <p>
     * Se la query inizia con '?' non viene aggiunto un secondo prefisso.
     * Se la query è {@code null} o vuota, restituisce il percorso invariato.
     *
     * @param path  il percorso base
     * @param query la stringa di query da aggiungere; può iniziare con '?' oppure essere una stringa nuda
     * @return il percorso completo con la query aggiunta, o il percorso invariato se la query è {@code null} o vuota
     */
    private static String appendQuery(String path, String query) {
        if (query == null || query.isBlank()) return path;
        String trimmed = query.strip();
        if (trimmed.startsWith("?")) return path + trimmed;
        return path + "?" + trimmed;
    }

    /**
     * Tronca una stringa alla lunghezza massima specificata,
     * aggiungendo un carattere di ellissi se necessario.
     *
     * @param s   la stringa da troncare; se {@code null} restituisce una stringa vuota
     * @param max la lunghezza massima consentita; deve essere positiva
     * @return la stringa troncata, o una stringa vuota se l'input è {@code null}
     */
    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ───────────────────────────── singleton accessor ──────────────

    private static volatile ApiClient cached;

    /**
     * Restituisce l'unica istanza singleton di {@code ApiClient}.
     * <p>
     * L'URL di base viene risolto al primo invocazione dalla variabile
     * d'ambiente {@code LOCAL_SERVER_URL}, con default {@link #DEFAULT_BASE_URL}.
     *
     * @return l'istanza singleton di {@code ApiClient}
     * @see #setInstance(ApiClient)
     */
    public static ApiClient instance() {
        ApiClient local = cached;
        if (local == null) {
            synchronized (ApiClient.class) {
                local = cached;
                if (local == null) {
                    local = new ApiClient();
                    cached = local;
                }
            }
        }
        return local;
    }

    /**
     * Sostituisce l'istanza singleton memorizzata.
     * <p>
     * Metodo di utilità per i test manuali UI e per la sostituzione
     * in contesti di smoke test.
     *
     * @param client la nuova istanza di {@code ApiClient}; se {@code null}
     *               le successive invocazioni di {@link #instance()} creeranno
     *               una nuova istanza
     * @see #instance()
     */
    public static void setInstance(ApiClient client) {
        cached = client;
    }
}
