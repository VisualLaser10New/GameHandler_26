package com.gameplatform.central.infrastructure.config;

import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring configuration that wires up the {@link JwtTokenProvider} bean.
 *
 * <p>Both the key path and the token expiration are read from application
 * properties so that no security-sensitive value is hard-coded in source.</p>
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(ResourceLoader resourceLoader,
                                             java.time.Clock clock,
                                             @Value("${jwt.private-key-path}") String privateKeyPath,
                                             @Value("${jwt.expiration-ms:86400000}") long tokenExpirationMs) {
        return new JwtTokenProvider(resourceLoader, clock, privateKeyPath, tokenExpirationMs);
    }
}
