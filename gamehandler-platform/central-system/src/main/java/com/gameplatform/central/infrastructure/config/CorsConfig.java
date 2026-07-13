package com.gameplatform.central.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for the central-system.
 *
 * <p>Allowed origins are read from the {@code cors.allowed-origins} property
 * (comma-separated list, default: {@code http://localhost:3000}).  When the
 * property is set to the literal {@code *} (wildcard) the filter allows all
 * origins but <strong>without credentials</strong>, in compliance with the
 * CORS specification (credentials + wildcard origin is forbidden by browsers).
 * When specific origins are listed, credentials are enabled.</p>
 *
 * <h3>Property example</h3>
 * <pre>
 * # single origin
 * cors.allowed-origins=http://localhost:3000
 *
 * # multiple origins
 * cors.allowed-origins=https://app.example.com,https://admin.example.com
 *
 * # allow-all (no credentials)
 * cors.allowed-origins=*
 * </pre>
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * Comma-separated list of allowed CORS origins.
     * Defaults to {@code http://localhost:3000} for local development.
     */
    private final String allowedOriginsRaw;

    public CorsConfig(@Value("${cors.allowed-origins:http://localhost:3000}") String allowedOriginsRaw) {
        this.allowedOriginsRaw = allowedOriginsRaw;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        boolean isWildcard = "*".equals(allowedOriginsRaw.trim());

        if (isWildcard) {
            // Wildcard origin — credentials MUST be disabled (CORS spec §3.2).
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowCredentials(false);
            log.warn("CORS configured with wildcard origin ('*'). Credentials are disabled. " +
                     "For production, set cors.allowed-origins to explicit origins.");
        } else {
            List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            config.setAllowedOrigins(origins);
            config.setAllowCredentials(true);
            log.info("CORS configured with explicit origins: {} (credentials enabled)", origins);
        }

        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
