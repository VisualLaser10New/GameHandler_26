package com.gameplatform.central.infrastructure.security;

import com.gameplatform.central.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final ResourceLoader resourceLoader;
    private final String privateKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public JwtTokenProvider(ResourceLoader resourceLoader, String privateKeyPath) {
        this.resourceLoader = resourceLoader;
        this.privateKeyPath = privateKeyPath;
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
                        log.warn("Loaded private key is not an RSAPrivateCrtKey, generating a temporary key pair instead");
                        generateFallbackKeyPair();
                    }
                }
            } else {
                log.warn("Private key file not found at {}. Generating a temporary RSA key pair for this session...", privateKeyPath);
                generateFallbackKeyPair();
            }
        } catch (Exception e) {
            log.error("Failed to load RSA private key from {}. Generating a temporary RSA key pair...", privateKeyPath, e);
            generateFallbackKeyPair();
        }
    }

    private void generateFallbackKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey = kp.getPublic();
            log.info("Temporary fallback RSA key pair generated successfully");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate fallback RSA key pair", e);
        }
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);
        
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId().value())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

