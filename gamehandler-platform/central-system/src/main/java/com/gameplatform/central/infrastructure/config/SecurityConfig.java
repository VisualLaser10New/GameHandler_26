package com.gameplatform.central.infrastructure.config;

import com.gameplatform.central.infrastructure.security.InternalApiKeyFilter;
import com.gameplatform.central.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configurazione della sicurezza HTTP per il central-system.
 *
 * <p>Definisce la catena di filtri di sicurezza Spring Security, disabilitando
 * CSRF, abilitando CORS, impostando la gestione della sessione come stateless
 * e registrando i filtri personalizzati {@link JwtAuthenticationFilter} e
 * {@link InternalApiKeyFilter}. Le richieste verso gli endpoint pubblici
 * ({@code /actuator/health}, {@code /api/auth/**}, {@code /api/users},
 * {@code /internal/**}) sono consentite senza autenticazione; ogni altra
 * richiesta richiede un utente autenticato.</p>
 *
 * @see JwtAuthenticationFilter
 * @see InternalApiKeyFilter
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    /**
     * Costruisce la configurazione di sicurezza con i filtri specificati.
     *
     * @param jwtAuthenticationFilter filtro per l'autenticazione tramite JWT
     * @param internalApiKeyFilter    filtro per l'autenticazione tramite API key interna
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          InternalApiKeyFilter internalApiKeyFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
    }

    /**
     * Crea e restituisce la catena di filtri di sicurezza Spring Security.
     *
     * @param http l'oggetto {@link HttpSecurity} su cui configurare le policy di sicurezza
     * @return la catena di filtri di sicurezza costruita
     * @throws Exception se si verifica un errore durante la configurazione della catena di filtri
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/users").permitAll() // registration POST endpoint
                .requestMatchers("/internal/**").permitAll() // Handled by InternalApiKeyFilter
                .anyRequest().authenticated()
            );

        return http.build();
    }
}

