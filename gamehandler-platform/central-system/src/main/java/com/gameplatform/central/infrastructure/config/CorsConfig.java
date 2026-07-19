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
 * Configurazione CORS per il central-system.
 *
 * <p>Definisce la sorgente di configurazione CORS applicata a tutte le richieste
 * in ingresso. Le origini consentite sono lette dalla proprietà
 * {@code cors.allowed-origins} (lista separata da virgole, valore predefinito
 * {@code http://localhost:3000}). Quando la proprietà assume il valore letterale
 * {@code *} (wildcard) il filtro ammette qualsiasi origine ma <strong>senza
 * credenziali</strong>, in conformità alla specifica CORS (l'uso combinato di
 * credenziali e origine jolly è vietato dai browser). Quando sono elencate origini
 * esplicite, le credenziali vengono abilitate.</p>
 *
 * <h3>Esempio di proprietà</h3>
 * <pre>
 * # singola origine
 * cors.allowed-origins=http://localhost:3000
 *
 * # origini multiple
 * cors.allowed-origins=https://app.example.com,https://admin.example.com
 *
 * # tutte le origini (senza credenziali)
 * cors.allowed-origins=*
 * </pre>
 *
 * @see org.springframework.web.cors.CorsConfigurationSource
 * @see org.springframework.web.cors.UrlBasedCorsConfigurationSource
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * Comma-separated list of allowed CORS origins.
     * Defaults to {@code http://localhost:3000} for local development.
     */
    private final String allowedOriginsRaw;

    /**
     * Costruisce una nuova configurazione CORS.
     *
     * @param allowedOriginsRaw stringa contenente le origini consentite separate da virgola,
     *                          oppure {@code "*"} per abilitare tutte le origini; valorizzata
     *                          dalla proprietà {@code cors.allowed-origins}
     */
    public CorsConfig(@Value("${cors.allowed-origins:http://localhost:3000}") String allowedOriginsRaw) {
        this.allowedOriginsRaw = allowedOriginsRaw;
    }

    /**
     * Crea e restituisce la sorgente di configurazione CORS applicata a tutte le route.
     *
     * <p>Se la proprietà {@code cors.allowed-origins} è impostata a {@code "*"}, la
     * configurazione abilita tutte le origini ma disabilita le credenziali, in conformità
     * con la specifica CORS. Altrimenti, le origini esplicite vengono configurate con
     * supporto per le credenziali.</p>
     *
     * @return la sorgente di configurazione CORS registrata per tutte le route ({@code /**})
     */
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
