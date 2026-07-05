package com.gameplatform.local.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final String configuredApiKey;

    public InternalApiKeyFilter(@Value("${internal.api-key:secret}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/internal/")) {
            String apiKeyHeader = request.getHeader("X-Internal-Api-Key");

            boolean isValid = false;
            if (apiKeyHeader != null && configuredApiKey != null) {
                isValid = java.security.MessageDigest.isEqual(
                        apiKeyHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        configuredApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            }

            if (!isValid) {
                log.warn("Unauthorized attempt to access local internal endpoint: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Missing or invalid internal API key.\"}");
                return;
            }

            log.debug("Internal API key validated successfully for path: {}", path);
        }

        filterChain.doFilter(request, response);
    }
}

