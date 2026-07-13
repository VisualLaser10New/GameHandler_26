package com.gameplatform.central.infrastructure.security;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Servlet filter that protects all {@code /internal/**} endpoints with a
 * shared-secret API key passed in the {@code X-Internal-Api-Key} request header.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>At application startup the {@code internal.api-key} property is validated:
 *       if it is blank or null an {@link IllegalStateException} is thrown, preventing
 *       the application from starting in an insecure configuration.</li>
 *   <li>Requests to {@code /internal/**} without the header, or with a header that
 *       does not match the configured key, are rejected with {@code 403 Forbidden}.
 *       The comparison uses a constant-time algorithm to mitigate timing attacks.</li>
 *   <li>Requests to other paths are passed through without modification.</li>
 * </ul>
 */
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
     * Validates the configured API key at startup.
     *
     * <p>A blank or null key would allow every request to pass, which is an
     * insecure misconfiguration. The application must not start in this state.
     * A non-blank key equal to the default {@code "secret"} is accepted (so
     * local dev without {@code INTERNAL_API_KEY} still works) but emits a
     * WARNING when the active profile is not {@code dev}, {@code test} or
     * {@code e2e}, to surface the misconfiguration in production-like
     * environments without breaking startup.</p>
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
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/internal/")) {
            String apiKeyHeader = request.getHeader("X-Internal-Api-Key");

            if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
                log.warn("Rejected internal request to {} — missing X-Internal-Api-Key header", path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Missing X-Internal-Api-Key header");
                return;
            }

            boolean isValid = MessageDigest.isEqual(
                    apiKeyHeader.getBytes(StandardCharsets.UTF_8),
                    configuredApiKey.getBytes(StandardCharsets.UTF_8)
            );

            if (!isValid) {
                log.warn("Rejected internal request to {} — invalid API key", path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid internal API key");
                return;
            }

            log.debug("Internal API key validated successfully for path: {}", path);
        }

        filterChain.doFilter(request, response);
    }
}
