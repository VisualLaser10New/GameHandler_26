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
 * Provides JWT token generation and validation using RSA asymmetric keys.
 *
 * <p>The token expiration duration is fully configurable via the
 * {@code jwt.expiration-ms} property (default: 86 400 000 ms = 24 hours),
 * eliminating any hard-coded expiration constants.</p>
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
     * Constructs a {@code JwtTokenProvider}.
     *
     * @param resourceLoader   Spring resource loader used to resolve the PEM file.
     * @param clock            Clock instance for time synchronization.
     * @param privateKeyPath   Classpath or filesystem path to the PKCS-8 RSA private key.
     * @param tokenExpirationMs Token lifetime in milliseconds (from {@code jwt.expiration-ms}).
     */
    public JwtTokenProvider(ResourceLoader resourceLoader, Clock clock, String privateKeyPath, long tokenExpirationMs) {
        this.resourceLoader = resourceLoader;
        this.clock = clock;
        this.privateKeyPath = privateKeyPath;
        this.tokenExpirationMs = tokenExpirationMs;
    }

    /**
     * Constructs a {@code JwtTokenProvider} using default UTC clock for backward compatibility.
     */
    @Deprecated
    public JwtTokenProvider(ResourceLoader resourceLoader, String privateKeyPath, long tokenExpirationMs) {
        this(resourceLoader, Clock.systemUTC(), privateKeyPath, tokenExpirationMs);
    }

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

    @Override
    @Deprecated(since = "B11", forRemoval = true)
    public String generateToken(User user, Instant now) {
        return generateTokenWithExpiry(user, now).token();
    }

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

    public String generateToken(User user) {
        return generateToken(user, Instant.now(clock));
    }

    /**
     * Validates the given JWT token.
     *
     * <p>Returns {@code true} if the token is correctly signed and not expired;
     * {@code false} otherwise. This method never throws.</p>
     *
     * @param token the compact JWT string to validate
     * @return {@code true} if the token is valid, {@code false} if it is
     *         malformed, expired, or has an invalid signature
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
     * Parses and returns the claims from a valid JWT token.
     *
     * @param token the compact JWT string
     * @return the {@link Claims} payload
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── package-private accessor for tests ───────────────────────────────────

    /** Returns the configured token expiration in milliseconds. */
    @Override
    public long getTokenExpirationMs() {
        return tokenExpirationMs;
    }
}
