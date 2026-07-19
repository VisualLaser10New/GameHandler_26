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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe di utilità per la creazione di istanze {@link HttpClient} e la gestione
 * dello stato di autenticazione della sessione corrente.
 * <p>
 * Se l'URL del server locale utilizza il protocollo HTTPS, tenta di caricare
 * il truststore dinamico per verificare in modo sicuro il certificato del server.
 * Mantiene lo stato di autenticazione per-sessione (token JWT, nome utente, ID utente,
 * ruoli ed edifici) popolato dopo la risoluzione di {@code /api/auth/me}.
 * <p>
 * I campi {@code roles} e {@code buildings} vengono popolati dopo che
 * {@code /api/auth/me} risolve il DTO {@code UserInfoDto} arricchito, consentendo
 * all'interfaccia utente di guidare una navbar basata sui ruoli senza decodificare il JWT.
 *
 * @see CertificateEnrollmentService
 */
public class HttpClientHelper {

    private static final Logger log = LoggerFactory.getLogger(HttpClientHelper.class);

    private static volatile String token;
    private static volatile String currentUsername;
    private static volatile String currentUserId;
    private static volatile List<String> roles = List.of();
    private static volatile List<String> buildings = List.of();

    /**
     * Imposta il token di autenticazione JWT per la sessione corrente.
     *
     * @param t il token JWT, può essere {@code null} per indicare assenza di autenticazione
     * @see #getToken()
     * @see #clearSession()
     */
    public static void setToken(String t) {
        token = t;
    }

    /**
     * Restituisce il token di autenticazione JWT della sessione corrente.
     *
     * @return il token JWT, {@code null} se non è stato impostato o la sessione è stata cancellata
     * @see #setToken(String)
     * @see #clearSession()
     */
    public static String getToken() {
        return token;
    }

    /**
     * Imposta il nome utente dell'utente autenticato per la sessione corrente.
     *
     * @param username il nome utente, può essere {@code null}
     * @see #getCurrentUsername()
     * @see #clearSession()
     */
    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    /**
     * Restituisce il nome utente dell'utente autenticato nella sessione corrente.
     *
     * @return il nome utente, {@code null} se non è stato impostato o la sessione è stata cancellata
     * @see #setCurrentUsername(String)
     * @see #clearSession()
     */
    public static String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Imposta l'identificativo univoco dell'utente autenticato per la sessione corrente.
     *
     * @param userId l'identificativo utente, può essere {@code null}
     * @see #getCurrentUserId()
     * @see #clearSession()
     */
    public static void setCurrentUserId(String userId) {
        currentUserId = userId;
    }

    /**
     * Restituisce l'identificativo univoco dell'utente autenticato nella sessione corrente.
     *
     * @return l'identificativo utente, {@code null} se non è stato impostato o la sessione è stata cancellata
     * @see #setCurrentUserId(String)
     * @see #clearSession()
     */
    public static String getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Memorizza i ruoli dell'utente autenticato (stringhe semplici come
     * {@code "PLAYER"}, {@code "PLATFORM_ADMIN"} — senza prefisso {@code ROLE_}).
     * <p>
     * Viene creata una copia difensiva della lista per evitare che mutazioni
     * esterne possano corrompere lo stato della sessione.
     *
     * @param r la lista dei ruoli; se {@code null} viene convertita in lista vuota
     * @see #getRoles()
     * @see #hasRole(String)
     * @see #clearSession()
     */
    public static void setRoles(List<String> r) {
        roles = r == null ? List.of() : List.copyOf(r);
    }

    /**
     * Restituisce la lista dei ruoli dell'utente autenticato.
     *
     * @return la lista dei ruoli, mai {@code null}; una lista vuota se non impostata,
     *         se impostata a {@code null} o dopo {@link #clearSession()}
     * @see #setRoles(List)
     * @see #hasRole(String)
     */
    public static List<String> getRoles() {
        return roles;
    }

    /**
     * Memorizza gli edifici di cui l'utente autenticato è {@code LOCAL_ADMIN}
     * (lista vuota per ruoli non amministrativi).
     * <p>
     * Popolata da {@code UserInfoDto.buildings} risolta da {@code /api/auth/me}.
     * Viene creata una copia difensiva della lista per evitare che mutazioni
     * esterne possano corrompere lo stato della sessione.
     *
     * @param b la lista degli edifici; se {@code null} viene convertita in lista vuota
     * @see #getBuildings()
     * @see #clearSession()
     */
    public static void setBuildings(List<String> b) {
        buildings = b == null ? List.of() : List.copyOf(b);
    }

    /**
     * Restituisce la lista degli edifici di cui l'utente autenticato è amministratore locale.
     *
     * @return la lista degli edifici, mai {@code null}; una lista vuota se non impostata,
     *         se impostata a {@code null} o dopo {@link #clearSession()}
     * @see #setBuildings(List)
     */
    public static List<String> getBuildings() {
        return buildings;
    }

    /**
     * Verifica se l'utente autenticato possiede il ruolo specificato.
     * Il confronto è case-sensitive e non applica alcun prefisso al ruolo fornito.
     *
     * @param role il nome del ruolo da verificare (es. {@code "PLAYER"}), può essere {@code null}
     * @return {@code true} se l'utente possiede il ruolo indicato;
     *         {@code false} se il ruolo è {@code null}, se la lista dei ruoli è vuota
     *         o se il ruolo non è presente
     * @see #setRoles(List)
     * @see #getRoles()
     */
    public static boolean hasRole(String role) {
        if (role == null) return false;
        List<String> current = roles;
        return current != null && current.contains(role);
    }

    /**
     * Cancella tutti i campi della sessione corrente.
     * <p>
     * Invocato durante il logout per garantire che il prossimo login
     * parta da uno stato pulito. Dopo l'invocazione, tutti i getter
     * restituiscono {@code null} o lista vuota.
     *
     * @see #setToken(String)
     * @see #setCurrentUsername(String)
     * @see #setCurrentUserId(String)
     * @see #setRoles(List)
     * @see #setBuildings(List)
     */
    public static void clearSession() {
        token = null;
        currentUsername = null;
        currentUserId = null;
        roles = List.of();
        buildings = List.of();
    }

    /**
     * Crea e restituisce un'istanza di {@link HttpClient} configurata per comunicare
     * con il server locale.
     * <p>
     * Se l'URL utilizza il protocollo HTTPS, tenta di caricare il truststore prima
     * dal filesystem ({@code certs/local-truststore.p12}) e, in alternativa, dal
     * classpath ({@code /local-truststore.p12}) per abilitare la verifica TLS.
     * Se il caricamento del truststore fallisce per qualsiasi motivo, viene restituito
     * un client HTTP predefinito senza verifica TLS.
     * Se l'URL utilizza il protocollo HTTP, restituisce direttamente un client HTTP
     * predefinito.
     *
     * @param localServerUrl l'URL di base del server locale (non nullo)
     * @return un'istanza di {@link HttpClient} configurata con il truststore appropriato
     *         se disponibile, altrimenti un client predefinito
     * @see CertificateEnrollmentService
     */
    public static HttpClient getHttpClient(String localServerUrl) {
        if (localServerUrl.startsWith("https://")) {
            log.info("HttpClient TLS enabled — base URL: {}", localServerUrl);
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

                    log.info("HttpClient truststore loaded from filesystem: {}", truststoreFile.getPath());
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

                            log.info("HttpClient truststore loaded from classpath: /local-truststore.p12");
                            return HttpClient.newBuilder()
                                    .sslContext(sslContext)
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("HttpClient TLS init failed for {}: {} — falling back to default HttpClient", localServerUrl, e.getMessage());
            }
        } else {
            log.warn("HttpClient TLS DISABLED — base URL {} is not https://; Local Server requires TLS (expected HTTP 400 'requires TLS' otherwise)", localServerUrl);
        }
        return HttpClient.newHttpClient();
    }
}
