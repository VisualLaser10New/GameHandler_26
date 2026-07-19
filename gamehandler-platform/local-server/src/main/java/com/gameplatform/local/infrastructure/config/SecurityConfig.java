package com.gameplatform.local.infrastructure.config;

import com.gameplatform.local.infrastructure.security.InternalApiKeyFilter;
import com.gameplatform.local.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configurazione della sicurezza HTTP per il server locale.
 * <p>
 * Disabilita CSRF, imposta una politica di sessione stateless e registra i filtri
 * {@link com.gameplatform.local.infrastructure.security.JwtAuthenticationFilter} e
 * {@link com.gameplatform.local.infrastructure.security.InternalApiKeyFilter} per l'autenticazione
 * delle richieste. Definisce le regole di autorizzazione per gli endpoint pubblici e protetti.
 * </p>
 *
 * @see com.gameplatform.local.infrastructure.security.JwtAuthenticationFilter
 * @see com.gameplatform.local.infrastructure.security.InternalApiKeyFilter
 * @see org.springframework.security.web.SecurityFilterChain
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    /**
     * Costruisce una nuova configurazione di sicurezza con i filtri di autenticazione.
     *
     * @param jwtAuthenticationFilter il filtro per l'autenticazione tramite token JWT
     * @param internalApiKeyFilter    il filtro per l'autenticazione tramite API key interna
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          InternalApiKeyFilter internalApiKeyFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
    }

    /**
     * Configura la catena di filtri di sicurezza Spring Security.
     * <p>
     * Disabilita la protezione CSRF, aggiunge i filtri JWT e API key prima del filtro
     * {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter},
     * imposta una politica di sessione stateless e definisce le regole di autorizzazione
     * per gli endpoint dell'applicazione.
     * </p>
     *
     * @param http l'oggetto {@link HttpSecurity} da configurare
     * @return la catena di filtri di sicurezza costruita
     * @throws Exception se si verifica un errore durante la configurazione della sicurezza
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/internal/**").permitAll() // Handled by InternalApiKeyFilter
                .anyRequest().authenticated()
            );

        return http.build();
    }
}

