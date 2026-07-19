package com.gameplatform.central.infrastructure.config;

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
 * Configurazione TLS per il central-system.
 *
 * <p>Crea il bean {@link SSLContext} utilizzato per le connessioni TLS in uscita.
 * Se le proprietà {@code ssl.trust-store} e {@code ssl.trust-store-password} sono
 * impostate e non vuote, il contesto viene inizializzato con un truststore
 * personalizzato in formato PKCS12; in caso contrario viene utilizzato il
 * truststore predefinito della JVM.</p>
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
     * Costruisce una nuova configurazione TLS.
     *
     * @param resourceLoader caricatore di risorse per risolvere il percorso del truststore
     */
    public TlsConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Crea e restituisce il contesto TLS utilizzato per le connessioni sicure in uscita.
     *
     * <p>Se la proprietà {@code ssl.trust-store} è vuota, non impostata o contiene
     * il placeholder non risolto, viene restituito il contesto TLS predefinito della
     * JVM. Altrimenti, il contesto viene inizializzato con un truststore personalizzato
     * in formato PKCS12 caricato dal percorso specificato.</p>
     *
     * @return il contesto SSL/TLS configurato
     * @throws RuntimeException se il caricamento del truststore o l'inizializzazione
     *                          del contesto TLS fallisce
     */
    @Bean
    public SSLContext sslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (trustStorePath == null || trustStorePath.isBlank() || trustStorePath.startsWith("${")) {
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