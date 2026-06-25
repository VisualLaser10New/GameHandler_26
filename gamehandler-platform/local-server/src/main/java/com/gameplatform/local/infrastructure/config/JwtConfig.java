package com.gameplatform.local.infrastructure.config;

import com.gameplatform.local.infrastructure.security.JwtTokenProvider;
import com.gameplatform.local.infrastructure.security.JwtTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        try {
            log.info("Loading local private key from: {}", privateKeyPath);
            log.info("Loading local public key from: {}", publicKeyPath);

            Resource privateRes = resourceLoader.getResource(privateKeyPath);
            Resource publicRes = resourceLoader.getResource(publicKeyPath);

            if (privateRes.exists() && publicRes.exists()) {
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
            } else {
                log.warn("One or both local PEM key files not found. Generating temporary RSA keypair...");
                generateFallbackKeyPair();
            }
        } catch (Exception e) {
            log.error("Failed to load local RSA keys. Generating temporary fallback keypair...", e);
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
            log.info("Temporary fallback local RSA keypair generated successfully");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate fallback RSA keypair", e);
        }
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(privateKey);
    }

    @Bean
    public JwtTokenValidator jwtTokenValidator() {
        return new JwtTokenValidator(publicKey);
    }
}

