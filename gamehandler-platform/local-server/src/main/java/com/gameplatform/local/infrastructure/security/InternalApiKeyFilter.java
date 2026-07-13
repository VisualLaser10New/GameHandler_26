package com.gameplatform.local.infrastructure.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final String configuredApiKey;
    private final Environment environment;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String configuredApiKey, Environment environment) {
        this.configuredApiKey = configuredApiKey;
        this.environment = environment;
    }

    /**
     * Validates the configured API key at startup (mirrors the central-system
     * filter). A blank/null key fails fast with {@link IllegalStateException}.
     * A non-blank key equal to the default {@code "secret"} is accepted (so
     * local dev without {@code INTERNAL_API_KEY} still works) but emits a
     * WARNING when the active profile is not {@code dev}, {@code test} or
     * {@code e2e}, to surface the misconfiguration in production-like
     * environments without breaking startup.
     *
     * @throws IllegalStateException if {@code internal.api-key} is blank or null
     */
    @PostConstruct
    public void validateConfiguration() {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Security misconfiguration: 'internal.api-key' must not be blank. " +
                    "Set the property or the INTERNAL_API_KEY environment variable.");
        }
        if ("secret".equals(configuredApiKey)
                && !environment.acceptsProfiles(Profiles.of("test", "e2e", "dev"))) {
            log.warn("Security misconfiguration: 'internal.api-key' is using the default " +
                    "'secret' value. Set a strong INTERNAL_API_KEY in production-like " +
                    "environments (active profiles: {}).",
                    String.join(",", environment.getActiveProfiles()));
        }
        log.info("InternalApiKeyFilter initialized — internal API key is configured.");
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

