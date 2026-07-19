package com.gameplatform.local.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Configurazione del contesto SSL/TLS per la comunicazione crittografata.
 * <p>
 * Carica il truststore PKCS12 dalle proprietà di configurazione e inizializza
 * un {@link SSLContext} con i trust manager corrispondenti. In assenza di truststore
 * configurato, utilizza un contesto SSL di default (utile per test o ambienti di sviluppo).
 * </p>
 *
 * @see javax.net.ssl.SSLContext
 */
@Configuration
public class TlsConfig {

    @Value("${ssl.trust-store:}")
    private String trustStorePath;

    @Value("${ssl.trust-store-password:}")
    private String trustStorePassword;

    private final ResourceLoader resourceLoader;

    /**
     * Costruisce una nuova configurazione TLS con il loader di risorse specificato.
     *
     * @param resourceLoader il loader per la risoluzione del percorso del truststore
     */
    public TlsConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Crea e restituisce il bean {@link SSLContext} configurato con il truststore PKCS12.
     * <p>
     * Se il percorso del truststore non è configurato o è vuoto, restituisce un contesto SSL
     * non inizializzato con trust manager di default, utile per ambienti di test o sviluppo.
     * </p>
     *
     * @return il contesto SSL/TLS configurato
     * @throws RuntimeException se si verifica un errore durante l'inizializzazione del contesto SSL
     */
    @Bean
    public SSLContext sslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (trustStorePath == null || trustStorePath.isBlank() || trustStorePath.startsWith("${")) {
                // Fallback for tests or missing configuration
                sslContext.init(null, null, new SecureRandom());
                return sslContext;
            }

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = resourceLoader.getResource(trustStorePath).getInputStream()) {
                trustStore.load(in, trustStorePassword.toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSLContext bean with truststore", e);
        }
    }
}

