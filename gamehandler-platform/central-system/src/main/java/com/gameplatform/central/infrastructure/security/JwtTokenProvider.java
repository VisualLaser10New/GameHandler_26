package com.gameplatform.central.infrastructure.security;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.TokenProviderPort;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Provider per la generazione e la validazione di token JWT tramite chiavi
 * asimmetriche RSA.
 *
 * <p>La durata di validità del token è completamente configurabile tramite
 * la proprietà {@code jwt.expiration-ms} (default: 86.400.000 ms = 24 ore).
 * Implementa l'interfaccia {@link TokenProviderPort} per l'integrazione con
 * il dominio dell'applicazione.</p>
 *
 * @see TokenProviderPort
 * @see io.jsonwebtoken.Jwts
 */
public class JwtTokenProvider implements TokenProviderPort {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final ResourceLoader resourceLoader;
    private final Clock clock;
    private final String privateKeyPath;

    /** Token lifetime in milliseconds, injected from {@code jwt.expiration-ms}. */
    private final long tokenExpirationMs;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * Costruisce un {@code JwtTokenProvider}.
     *
     * @param resourceLoader   il caricatore di risorse Spring per risolvere il
     *                         file PEM della chiave privata
     * @param clock            l'orologio per la sincronizzazione temporale
     * @param privateKeyPath   il percorso della chiave privata RSA in formato
     *                         PKCS-8 (classpath o filesystem)
     * @param tokenExpirationMs la durata di validità del token in millisecondi
     *                         (dalla proprietà {@code jwt.expiration-ms})
     */
    public JwtTokenProvider(ResourceLoader resourceLoader, Clock clock, String privateKeyPath, long tokenExpirationMs) {
        this.resourceLoader = resourceLoader;
        this.clock = clock;
        this.privateKeyPath = privateKeyPath;
        this.tokenExpirationMs = tokenExpirationMs;
    }

    /**
     * Costruisce un {@code JwtTokenProvider} utilizzando l'orologio UTC
     * predefinito per compatibilità con le versioni precedenti.
     *
     * @param resourceLoader   il caricatore di risorse Spring per risolvere il
     *                         file PEM della chiave privata
     * @param privateKeyPath   il percorso della chiave privata RSA in formato
     *                         PKCS-8 (classpath o filesystem)
     * @param tokenExpirationMs la durata di validità del token in millisecondi
     *
     * @deprecated utilizzare {@link #JwtTokenProvider(ResourceLoader, Clock, String, long)}
     *             specificando un orologio esplicito
     */
    @Deprecated
    public JwtTokenProvider(ResourceLoader resourceLoader, String privateKeyPath, long tokenExpirationMs) {
        this(resourceLoader, Clock.systemUTC(), privateKeyPath, tokenExpirationMs);
    }

    /**
     * Inizializza il provider caricando la chiave privata RSA dal percorso
     * configurato e derivando la corrispondente chiave pubblica.
     *
     * <p>Il metodo viene invocato automaticamente dopo la costruzione del
     * bean grazie all'annotazione {@link PostConstruct}.</p>
     *
     * @throws IllegalStateException se il file della chiave privata non viene
     *                               trovato, non è in formato RSA valido o
     *                               non è possibile derivare la chiave pubblica
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Loading private key from path: {}", privateKeyPath);
            Resource resource = resourceLoader.getResource(privateKeyPath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] keyBytes = is.readAllBytes();
                    String pem = new String(keyBytes, StandardCharsets.UTF_8);
                    String cleanPem = pem
                             .replace("-----BEGIN PRIVATE KEY-----", "")
                             .replace("-----END PRIVATE KEY-----", "")
                             .replaceAll("\\s+", "");
                    byte[] decoded = Base64.getDecoder().decode(cleanPem);
                    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    this.privateKey = kf.generatePrivate(spec);

                    if (this.privateKey instanceof RSAPrivateCrtKey crtKey) {
                        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
                        this.publicKey = kf.generatePublic(publicKeySpec);
                        log.info("Successfully loaded RSA private key and derived public key");
                    } else {
                        throw new IllegalStateException("Loaded private key from " + privateKeyPath + " is not an RSAPrivateCrtKey; cannot derive public key");
                    }
                }
            } else {
                throw new IllegalStateException("Private key file not found at " + privateKeyPath + "; aborting startup");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA private key from " + privateKeyPath + "; aborting startup", e);
        }
    }

    /**
     * Genera un token JWT per l'utente specificato con il timestamp fornito.
     *
     * @param user l'utente per cui generare il token, non nullo
     * @param now  l'istante di emissione del token
     * @return il token JWT compatto
     *
     * @deprecated utilizzare {@link #generateTokenWithExpiry(User, Instant)}
     *             che restituisce anche la data di scadenza
     */
    @Override
    @Deprecated(since = "B11", forRemoval = true)
    public String generateToken(User user, Instant now) {
        return generateTokenWithExpiry(user, now).token();
    }

    /**
     * Genera un token JWT per l'utente specificato e restituisce sia il
     * token sia la data di scadenza.
     *
     * @param user l'utente per cui generare il token, non nullo
     * @param now  l'istante di emissione del token
     * @return un {@link TokenWithExpiry} contenente il token JWT compatto
     *         e l'istante di scadenza
     */
    @Override
    public TokenWithExpiry generateTokenWithExpiry(User user, Instant now) {
        Instant expiresAt = now.plusMillis(tokenExpirationMs);

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId().value())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new TokenWithExpiry(token, expiresAt);
    }

    /**
     * Genera un token JWT per l'utente specificato utilizzando l'orologio
     * configurato per determinare l'istante di emissione.
     *
     * @param user l'utente per cui generare il token, non nullo
     * @return il token JWT compatto
     */
    public String generateToken(User user) {
        return generateToken(user, Instant.now(clock));
    }

    /**
     * Verifica la validità del token JWT specificato.
     *
     * <p>Restituisce {@code true} se il token è correttamente firmato e non
     * è scaduto; {@code false} altrimenti. Questo metodo non lancia mai
     * eccezioni.</p>
     *
     * @param token la stringa JWT compatta da validare
     * @return {@code true} se il token è valido, {@code false} se è
     *         malformato, scaduto o ha una firma non valida
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Analizza e restituisce i claims dal token JWT specificato.
     *
     * @param token la stringa JWT compatta
     * @return il payload dei {@link Claims}
     * @throws io.jsonwebtoken.JwtException se il token non è valido o è
     *                                      scaduto
     * @see #validateToken(String)
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── package-private accessor for tests ───────────────────────────────────

    /**
     * Restituisce la durata di validità configurata per i token.
     *
     * @return la durata di validità in millisecondi
     */
    @Override
    public long getTokenExpirationMs() {
        return tokenExpirationMs;
    }
}
