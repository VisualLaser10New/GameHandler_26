package com.gameplatform.central.infrastructure.config;

import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(ResourceLoader resourceLoader,
                                             @Value("${jwt.private-key-path}") String privateKeyPath) {
        return new JwtTokenProvider(resourceLoader, privateKeyPath);
    }
}

