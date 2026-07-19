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

/**
 * Filtro che protegge gli endpoint interni (prefisso {@code /internal/})
 * richiedendo una chiave API condivisa trasmessa nell'header
 * {@code X-Internal-Api-Key}.
 *
 * <p>Esteende {@link OncePerRequestFilter} per garantire un'esecuzione
 * singola per ogni richiesta. La chiave configurata è letta dalla proprietà
 * {@code internal.api-key}. Il filtro confronta il valore dell'header con la
 * chiave configurata utilizzando un confronto time-constant
 * ({@link java.security.MessageDigest#isEqual}) per prevenire attacchi
 * timing side-channel.</p>
 *
 * <p>All'avvio, {@link #validateConfiguration()} controlla che la chiave
 * non sia vuota e segnala un avviso se è ancora impostata al valore
 * predefinito {@code "secret"} in ambienti non di sviluppo.</p>
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final String configuredApiKey;
    private final Environment environment;

    /**
     * Costruisce il filtro con la chiave API interna configurata e
     * l'ambiente Spring.
     *
     * @param configuredApiKey valore della proprietà {@code internal.api-key}
     * @param environment      ambiente Spring per la verifica dei profili
     *                         attivi
     */
    public InternalApiKeyFilter(@Value("${internal.api-key}") String configuredApiKey, Environment environment) {
        this.configuredApiKey = configuredApiKey;
        this.environment = environment;
    }

    /**
     * Valida la configurazione della chiave API interna all'avvio
     * dell'applicazione.
     *
     * <p>Se la chiave è vuota o nulla, viene sollevata un'eccezione
     * {@link IllegalStateException} per un fail-fast. Se la chiave è
     * impostata al valore predefinito {@code "secret"} ma il profilo
     * attivo non è {@code dev}, {@code test} o {@code e2e}, viene
     * emesso un avviso nei log per segnalare la potenziale
     * misconfigurazione in ambienti production-like.</p>
     *
     * @throws IllegalStateException se {@code internal.api-key} è vuota
     *                               o nulla
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

    /**
     * Applica il filtro di autenticazione interna per le richieste dirette
     * a percorsi con prefisso {@code /internal/}.
     *
     * <p>Per le richieste che non iniziano con {@code /internal/}, il
     * filtro passa la richiesta inalterata alla catena successiva. Per
     * le richieste interne, estrae l'header {@code X-Internal-Api-Key} e
     * lo confronta con la chiave configurata tramite
     * {@link java.security.MessageDigest#isEqual}. Se il confronto
     * fallisce, restituisce uno status 401 (Unauthorized).</p>
     *
     * @param request     richiesta HTTP in ingresso
     * @param response    risposta HTTP in uscita
     * @param filterChain catena dei filtri successivi
     * @throws ServletException in caso di errore nella gestione della
     *                          richiesta
     * @throws IOException      in caso di errore di I/O
     */
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

