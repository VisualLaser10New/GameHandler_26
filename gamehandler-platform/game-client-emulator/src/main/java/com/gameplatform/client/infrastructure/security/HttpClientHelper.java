package com.gameplatform.client.infrastructure.security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;

/**
 * Helper to build an HttpClient instance.
 * If the local server URL is HTTPS, it attempts to load the dynamic truststore
 * so it can securely verify the Local Server certificate.
 * <p>
 * Holds the per-session authentication state used by both the legacy
 * inline HTTP calls and the centralised {@code ApiClient} introduced in
 * FASE 7 §7.C: {@code token}, {@code currentUsername}, {@code roles}
 * and {@code buildings}. The latter two are populated after {@code /api/auth/me}
 * resolves the enriched {@code UserInfoDto} so the UI can drive a
 * role-aware navbar without decoding the JWT (PIANO §7.C line 728-729).
 */
public class HttpClientHelper {

    private static volatile String token;
    private static volatile String currentUsername;
    private static volatile List<String> roles = List.of();
    private static volatile List<String> buildings = List.of();

    public static void setToken(String t) {
        token = t;
    }

    public static String getToken() {
        return token;
    }

    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Stores the authenticated user's resolved roles (plain strings like
     * {@code "PLAYER"}, {@code "PLATFORM_ADMIN"} — no {@code ROLE_} prefix).
     * A defensive copy is kept so external mutation cannot corrupt the
     * session state.
     */
    public static void setRoles(List<String> r) {
        roles = r == null ? List.of() : List.copyOf(r);
    }

    public static List<String> getRoles() {
        return roles;
    }

    /**
     * Stores the buildings the authenticated user is a {@code LOCAL_ADMIN}
     * of (empty for non-admin roles). Populated from
     * {@code UserInfoDto.buildings} resolved by {@code /api/auth/me}.
     */
    public static void setBuildings(List<String> b) {
        buildings = b == null ? List.of() : List.copyOf(b);
    }

    public static List<String> getBuildings() {
        return buildings;
    }

    /** True if the user holds the given role (case-sensitive, no prefix). */
    public static boolean hasRole(String role) {
        if (role == null) return false;
        List<String> current = roles;
        return current != null && current.contains(role);
    }

    /**
     * Clears every session field — invoked by the {@code Logout} button
     * (PIANO §7.C line 730) so the next login starts from a clean state.
     */
    public static void clearSession() {
        token = null;
        currentUsername = null;
        roles = List.of();
        buildings = List.of();
    }

    public static HttpClient getHttpClient(String localServerUrl) {
        if (localServerUrl.startsWith("https://")) {
            try {
                File truststoreFile = new File("certs/local-truststore.p12");
                if (truststoreFile.exists()) {
                    KeyStore trustStore = KeyStore.getInstance("PKCS12");
                    try (InputStream in = new FileInputStream(truststoreFile)) {
                        trustStore.load(in, "changeit".toCharArray());
                    }
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);

                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                    return HttpClient.newBuilder()
                            .sslContext(sslContext)
                            .build();
                } else {
                    // Try to load from resources as a fallback
                    try (InputStream in = HttpClientHelper.class.getResourceAsStream("/local-truststore.p12")) {
                        if (in != null) {
                            KeyStore trustStore = KeyStore.getInstance("PKCS12");
                            trustStore.load(in, "changeit".toCharArray());
                            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                            tmf.init(trustStore);

                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

                            return HttpClient.newBuilder()
                                    .sslContext(sslContext)
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback to default HttpClient
            }
        }
        return HttpClient.newHttpClient();
    }
}
