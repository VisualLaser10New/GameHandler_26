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
 * Filtro servlet che protegge tutti gli endpoint {@code /internal/**} con una
 * chiave API condivisa trasmessa nell'header {@code X-Internal-Api-Key}.
 *
 * <p>All'avvio dell'applicazione la proprietà {@code internal.api-key} viene
 * validata: se risulta vuota o nulla il filtro impedisce l'avvio lanciando
 * un {@link IllegalStateException}. Le richieste verso {@code /internal/**}
 * prive dell'header o con un valore non corrispondente vengono rifiutate con
 * stato {@code 403 Forbidden}. Il confronto utilizza un algoritmo a tempo
 * costante per mitigare attacchi di tipo timing.</p>
 *
 * @see OncePerRequestFilter
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final String configuredApiKey;
    private final Environment environment;

    /**
     * Costruisce un {@code InternalApiKeyFilter} con la chiave API configurata
     * e l'ambiente Spring.
     *
     * @param configuredApiKey la chiave API configurata tramite la proprietà
     *                         {@code internal.api-key}, non nulla e non vuota
     * @param environment      l'ambiente Spring per la verifica dei profili
     *                         attivi
     */
    public InternalApiKeyFilter(@Value("${internal.api-key}") String configuredApiKey, Environment environment) {
        this.configuredApiKey = configuredApiKey;
        this.environment = environment;
    }

    /**
     * Valida la chiave API configurata all'avvio dell'applicazione.
     *
     * <p>Una chiave vuota o nulla rappresenta una configurazione insicura e
     * impedisce l'avvio dell'applicazione. Se la chiave corrisponde al valore
     * predefinito e il profilo attivo non è di sviluppo o test, viene emesso
     * un avvertimento nei log.</p>
     *
     * @throws IllegalStateException se la proprietà {@code internal.api-key}
     *                               è vuota o nulla
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
     * Applica il filtro di sicurezza alle richieste in ingresso.
     *
     * <p>Per le richieste verso percorsi che iniziano con {@code /internal/},
     * verifica la presenza e la correttezza dell'header
     * {@code X-Internal-Api-Key}. Se l'header è assente o non valido,
     * risponde con {@code 403 Forbidden}. Per tutti gli altri percorsi,
     * la richiesta viene lasciata proseguire senza modifiche.</p>
     *
     * @param request     la richiesta HTTP in ingresso
     * @param response    la risposta HTTP in uscita
     * @param filterChain la catena di filtri da invocare
     * @throws ServletException in caso di errore nella gestione della richiesta
     * @throws IOException      in caso di errore di I/O
     */
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
