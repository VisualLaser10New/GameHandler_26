package com.gameplatform.local.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.SecureRandom;

@Configuration
public class TlsConfig {

    @Bean
    public SSLContext sslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // Set up with default managers and a secure random instance
            sslContext.init(null, null, new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSLContext bean", e);
        }
    }
}

