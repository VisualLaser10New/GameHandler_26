package com.gameplatform.local.infrastructure.config;

import com.gameplatform.local.infrastructure.security.JwtTokenProvider;
import com.gameplatform.local.infrastructure.security.JwtTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final ResourceLoader resourceLoader;
    private final String privateKeyPath;
    private final String publicKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @Autowired(required = false)
    private java.time.Clock clock = java.time.Clock.systemUTC();

    public JwtConfig(
            ResourceLoader resourceLoader,
            @Value("${jwt.local-private-key-path}") String privateKeyPath,
            @Value("${jwt.local-public-key-path}") String publicKeyPath) {
        this.resourceLoader = resourceLoader;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        
        loadKeys();
    }

    private void loadKeys() {
        log.info("Loading local private key from: {}", privateKeyPath);
        log.info("Loading local public key from: {}", publicKeyPath);

        Resource privateRes = resourceLoader.getResource(privateKeyPath);
        Resource publicRes = resourceLoader.getResource(publicKeyPath);

        if (!privateRes.exists()) {
            throw new IllegalStateException("Local private key PEM file not found at " + privateKeyPath);
        }
        if (!publicRes.exists()) {
            throw new IllegalStateException("Local public key PEM file not found at " + publicKeyPath);
        }

        try {
            // Load Private Key
            byte[] privBytes;
            try (InputStream is = privateRes.getInputStream()) {
                privBytes = is.readAllBytes();
            }
            String privPem = new String(privBytes, StandardCharsets.UTF_8);
            String cleanPrivPem = privPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decodedPriv = Base64.getDecoder().decode(cleanPrivPem);
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(decodedPriv);

            // Load Public Key
            byte[] pubBytes;
            try (InputStream is = publicRes.getInputStream()) {
                pubBytes = is.readAllBytes();
            }
            String pubPem = new String(pubBytes, StandardCharsets.UTF_8);
            String cleanPubPem = pubPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decodedPub = Base64.getDecoder().decode(cleanPubPem);
            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(decodedPub);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.privateKey = kf.generatePrivate(privSpec);
            this.publicKey = kf.generatePublic(pubSpec);
            log.info("Successfully loaded local RSA keypair");
        } catch (Exception e) {
            log.error("Failed to load or parse local RSA keys from PEM files", e);
            throw new RuntimeException("Failed to load or parse local RSA keys", e);
        }
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(privateKey, clock);
    }

    @Bean
    public JwtTokenValidator jwtTokenValidator() {
        return new JwtTokenValidator(publicKey);
    }
}

