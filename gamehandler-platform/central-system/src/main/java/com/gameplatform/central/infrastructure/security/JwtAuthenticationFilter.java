package com.gameplatform.central.infrastructure.security;

import com.gameplatform.shared.domain.security.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro servlet che valida un token JWT di tipo Bearer su ogni richiesta
 * in ingresso.
 *
 * <p>Se l'header {@code Authorization} è assente o non inizia con
 * {@code Bearer}, il filtro lascia proseguire la richiesta senza modifiche.
 * Se il token Bearer è presente ma non valido, la richiesta viene
 * immediatamente rifiutata con stato {@code 401 Unauthorized} senza
 * invocare i filtri successivi.</p>
 *
 * @see OncePerRequestFilter
 * @see JwtTokenProvider
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Costruisce un {@code JwtAuthenticationFilter} con il provider di token
     * JWT per la validazione e l'estrazione dei claims.
     *
     * @param jwtTokenProvider il provider per la gestione dei token JWT,
     *                         non nullo
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Applica il filtro di autenticazione JWT alla richiesta in ingresso.
     *
     * <p>Se l'header {@code Authorization} contiene un token Bearer valido,
     * estrae i claims, costruisce un'istanza di
     * {@link UsernamePasswordAuthenticationToken} e la imposta nel contesto
     * di sicurezza di Spring. Se il token non è presente la richiesta
     * prosegue senza autenticazione; se è presente ma non valido viene
     * restituito errore {@code 401 Unauthorized}.</p>
     *
     * @param request     la richiesta HTTP in ingresso
     * @param response    la risposta HTTP in uscita
     * @param filterChain la catena di filtri da invocare
     * @throws ServletException in caso di errore nella gestione della richiesta
     * @throws IOException      in caso di errore di I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No Bearer token present — let the chain continue; security rules
            // will reject unauthenticated access to protected endpoints.
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            // Token present but invalid — reject immediately with 401.
            log.warn("Rejecting request to {} — invalid or expired JWT token", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
            return;
        }

        try {
            Claims claims = jwtTokenProvider.getClaims(token);
            String username = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = Role.toAuthorityNames(roles == null ? List.of() : roles)
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {} with roles: {}", username, roles);
            }
        } catch (Exception e) {
            // validateToken already passed, so this is an unexpected parse error.
            log.warn("Unexpected JWT parsing error for {}: {}", request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
