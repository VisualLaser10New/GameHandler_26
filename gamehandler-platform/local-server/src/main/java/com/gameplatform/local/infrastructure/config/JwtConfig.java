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

/**
 * Configurazione dei bean per la gestione dei token JWT basata su chiavi RSA asimmetriche.
 * <p>
 * Carica la coppia di chiavi pubblica e privata da file PEM all'avvio e fornisce
 * i bean {@link com.gameplatform.local.infrastructure.security.JwtTokenProvider} e
 * {@link com.gameplatform.local.infrastructure.security.JwtTokenValidator} per la creazione
 * e la validazione dei token.
 * </p>
 *
 * @see com.gameplatform.local.infrastructure.security.JwtTokenProvider
 * @see com.gameplatform.local.infrastructure.security.JwtTokenValidator
 */
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

    /**
     * Costruisce una nuova configurazione JWT, caricando immediatamente le chiavi RSA
     * dai percorsi specificati.
     *
     * @param resourceLoader  il loader per la risoluzione dei percorsi delle risorse
     * @param privateKeyPath  il percorso del file PEM contenente la chiave privata
     * @param publicKeyPath   il percorso del file PEM contenente la chiave pubblica
     * @throws IllegalStateException se uno dei file PEM non viene trovato
     * @throws RuntimeException      se si verifica un errore durante il parsing delle chiavi
     */
    public JwtConfig(
            ResourceLoader resourceLoader,
            @Value("${jwt.local-private-key-path}") String privateKeyPath,
            @Value("${jwt.local-public-key-path}") String publicKeyPath) {
        this.resourceLoader = resourceLoader;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        
        loadKeys();
    }

    /**
     * Carica e decodifica la coppia di chiavi RSA dai file PEM specificati nelle proprietà
     * di configurazione.
     * <p>
     * I file PEM vengono letti, ripuliti dai marcatori di inizio/fine e dagli spazi bianchi,
     * decodificati da Base64 e convertiti in oggetti {@link PrivateKey} e {@link PublicKey}
     * tramite {@link KeyFactory} con algoritmo RSA.
     * </p>
     *
     * @throws IllegalStateException se uno dei file PEM non viene trovato
     * @throws RuntimeException      se si verifica un errore durante la decodifica o il parsing delle chiavi
     */
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

    /**
     * Crea e restituisce il bean {@link JwtTokenProvider} per la generazione di token JWT.
     *
     * @return un nuovo provider di token JWT configurato con la chiave privata e l'orologio di sistema
     * @see JwtTokenProvider
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(privateKey, clock);
    }

    /**
     * Crea e restituisce il bean {@link JwtTokenValidator} per la validazione dei token JWT.
     *
     * @return un nuovo validatore di token JWT configurato con la chiave pubblica
     * @see JwtTokenValidator
     */
    @Bean
    public JwtTokenValidator jwtTokenValidator() {
        return new JwtTokenValidator(publicKey);
    }
}

