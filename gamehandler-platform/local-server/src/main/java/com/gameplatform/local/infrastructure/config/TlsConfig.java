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

@Configuration
public class TlsConfig {

    @Value("${ssl.trust-store:}")
    private String trustStorePath;

    @Value("${ssl.trust-store-password:}")
    private String trustStorePassword;

    private final ResourceLoader resourceLoader;

    public TlsConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

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

