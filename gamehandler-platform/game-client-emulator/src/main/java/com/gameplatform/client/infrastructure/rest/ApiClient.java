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
 * Centralised typed REST adapter for the Game Client Emulator
 * (PIANO §7.C line 724-725).
 * <p>
 * Wraps a singleton {@link HttpClient} built on top of
 * {@link HttpClientHelper#getHttpClient(String)} so HTTPS calls to the
 * Local Server re-use the project {@code local-truststore.p12}
 * truststore. The base URL is resolved once (env var
 * {@code LOCAL_SERVER_URL}, default {@code https://localhost:8181}) — this
 * is the single point of truth for the entire client.
 * <p>
 * Every typed method:
 * <ul>
 *   <li>attaches {@code Authorization: Bearer <token>} when a token is present in {@link HttpClientHelper};</li>
 *   <li>serialises the request body via the shared {@link ObjectMapper} configured with {@code JavaTimeModule};</li>
 *   <li>maps 401 → {@link AuthenticationException}, 403 →
 *       {@link AuthorizationException}, 5xx/timeout/unreachable →
 *       {@link ServerUnavailableException}, other 4xx →
 *       {@link HttpClientResponseException} (carrying the status code);</li>
 *   <li>deserialises the response body into the requested {@code Class<T>}.</li>
 * </ul>
 * <p>
 * Generic list responses require a {@link TypeReference} overload
 * because Jackson erases element types at runtime.
 * <p>
 * All methods are async and return {@link CompletableFuture}; the
 * JavaFX view layer is expected to marshal the callbacks back onto the
 * JavaFX Application Thread via {@code Platform.runLater}.
 */
public class ApiClient {

    /** Default base URL — overridable via {@code LOCAL_SERVER_URL} env var. */
    public static final String DEFAULT_BASE_URL = "https://localhost:8181";

    /**
     * Feature 2 — fixed dev mapping {@code buildingId → https://localhost:<port>}.
     * The docker-compose dev topology exposes every Local Server on a stable
     * localhost port, so the PLATFORM_ADMIN building selector can switch the
     * ApiClient base URL without resolving service DNS names (which won't
     * resolve on the dev host).
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
     * Lazy singleton — the first call resolves the base URL from the env
     * and builds the underlying {@link HttpClient} once for the JVM
     * lifetime. The constructor is public for testability but the
     * canonical accessor is {@link #instance()}.
     */
    public ApiClient() {
        this(System.getenv().getOrDefault("LOCAL_SERVER_URL", DEFAULT_BASE_URL));
    }

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        this.httpClient = HttpClientHelper.getHttpClient(this.baseUrl);
        this.mapper = ObjectMappers.SHARED;
    }

    /**
     * Feature 2 — dynamically retargets the singleton to a different Local
     * Server base URL (used by the PLATFORM_ADMIN building selector). The
     * underlying {@link HttpClient} is reused: every Local Server shares the
     * same TLS certificate (issued by the Local CA, SAN includes
     * {@code localhost}) so the original truststore validates all of them.
     * Only the host/port of subsequent requests changes.
     *
     * @param baseUrl the new base URL (e.g. {@code https://localhost:8182});
     *                a blank/null value is ignored
     */
    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ───────────────────────────── GET ─────────────────────────────

    /** Async GET to {@code path}, deserialised to a single object. */
    public <T> CompletableFuture<T> get(String path, Class<T> responseType) {
        return sendAsync(buildGET(path), responseType, null);
    }

    /** Async GET to {@code path}, deserialised to a generic list. */
    public <T> CompletableFuture<T> get(String path, TypeReference<T> typeRef) {
        return sendAsync(buildGET(path), null, typeRef);
    }

    /** Async GET to {@code path} with optional {@code ?query=value} suffix. */
    public <T> CompletableFuture<T> get(String path, String query, Class<T> responseType) {
        return sendAsync(buildGET(appendQuery(path, query)), responseType, null);
    }

    public <T> CompletableFuture<T> get(String path, String query, TypeReference<T> typeRef) {
        return sendAsync(buildGET(appendQuery(path, query)), null, typeRef);
    }

    // ───────────────────────────── POST ────────────────────────────

    /** Async POST to {@code path} with a JSON body, deserialised to {@code responseType}. */
    public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
        return sendAsync(buildPOST(path, body), responseType, null);
    }

    public <T> CompletableFuture<T> post(String path, Object body, TypeReference<T> typeRef) {
        return sendAsync(buildPOST(path, body), null, typeRef);
    }

    /** Async POST with no body (used for lifecycle actions like {@code /open}). */
    public <T> CompletableFuture<T> postEmpty(String path, Class<T> responseType) {
        return sendAsync(buildPOST(path, null), responseType, null);
    }

    // ───────────────────────────── PUT ─────────────────────────────

    public <T> CompletableFuture<T> put(String path, Object body, Class<T> responseType) {
        return sendAsync(buildPUT(path, body), responseType, null);
    }

    public <T> CompletableFuture<T> patch(String path, Object body, Class<T> responseType) {
        return sendAsync(buildRequest(path, "PATCH", serialize(body)), responseType, null);
    }

    // ───────────────────────────── DELETE ──────────────────────────

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

    private HttpRequest buildGET(String fullPath) {
        return buildRequest(fullPath, "GET", null);
    }

    private HttpRequest buildPOST(String path, Object body) {
        return buildRequest(path, "POST", serialize(body));
    }

    private HttpRequest buildPUT(String path, Object body) {
        return buildRequest(path, "PUT", serialize(body));
    }

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
     * Wraps transport-level failures (timeout, connection refused, generic
     * IOException) raised by {@link HttpClient#sendAsync} into
     * {@link ServerUnavailableException}, so callers can handle "Local Server
     * unreachable" with a single user-friendly catch clause. Domain
     * exceptions ({@link AuthenticationException}, {@link AuthorizationException},
     * {@link ServerUnavailableException} already raised for 401/403/5xx) and
     * other {@link RuntimeException}s are re-thrown unchanged so the existing
     * 401/403/4xx contract is preserved (PIANO §7.C — ApiClient gap 2).
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

    private String serialize(Object body) {
        if (body == null) return null;
        if (body instanceof String s) return s;
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body: " + e.getMessage(), e);
        }
    }

    private static String appendQuery(String path, String query) {
        if (query == null || query.isBlank()) return path;
        String trimmed = query.strip();
        if (trimmed.startsWith("?")) return path + trimmed;
        return path + "?" + trimmed;
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ───────────────────────────── singleton accessor ──────────────

    private static volatile ApiClient cached;

    /** Lazy JVM-singleton; the env var is read on first call. */
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

    /** Test hook: replaces the cached singleton (used by manual UI smoke tests). */
    public static void setInstance(ApiClient client) {
        cached = client;
    }
}