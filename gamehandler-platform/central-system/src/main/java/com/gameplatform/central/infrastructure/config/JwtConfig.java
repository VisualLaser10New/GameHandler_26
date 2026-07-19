package com.gameplatform.central.infrastructure.config;

import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Configurazione Spring che istanzia il bean {@link JwtTokenProvider}.
 *
 * <p>Sia il percorso della chiave privata sia la durata di validità del token
 * sono letti dalle proprietà dell'applicazione, in modo che nessun valore
 * sensibile per la sicurezza sia hard-coded nel codice sorgente.</p>
 *
 * @see JwtTokenProvider
 */
@Configuration
public class JwtConfig {

    /**
     * Crea e restituisce il bean {@link JwtTokenProvider} per la gestione dei token JWT.
     *
     * @param resourceLoader     caricatore di risorse per risolvere il percorso della chiave privata
     * @param clock              orologio di sistema per la generazione dei timestamp del token
     * @param privateKeyPath     percorso della chiave privata PEM, specificato dalla proprietà
     *                           {@code jwt.private-key-path}
     * @param tokenExpirationMs  durata di validità del token in millisecondi, specificata dalla
     *                           proprietà {@code jwt.expiration-ms} (default 86400000)
     * @return istanza configurata di {@link JwtTokenProvider}
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider(ResourceLoader resourceLoader,
                                             java.time.Clock clock,
                                             @Value("${jwt.private-key-path}") String privateKeyPath,
                                             @Value("${jwt.expiration-ms:86400000}") long tokenExpirationMs) {
        return new JwtTokenProvider(resourceLoader, clock, privateKeyPath, tokenExpirationMs);
    }
}
