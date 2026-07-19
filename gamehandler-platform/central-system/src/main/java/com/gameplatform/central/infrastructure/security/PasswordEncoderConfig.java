package com.gameplatform.central.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configurazione Spring per la codifica delle password.
 *
 * <p>Fornisce un bean {@link PasswordEncoder} basato su BCrypt per la
 * codifica e la verifica delle password degli utenti.</p>
 *
 * @see PasswordEncoder
 * @see BCryptPasswordEncoder
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Restituisce il codificatore di password BCrypt da utilizzare
     * nell'applicazione.
     *
     * @return un'istanza di {@link BCryptPasswordEncoder} con forza
     *         predefinita
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

