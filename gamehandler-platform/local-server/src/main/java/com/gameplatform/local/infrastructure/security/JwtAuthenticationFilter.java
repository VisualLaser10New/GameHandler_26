package com.gameplatform.local.infrastructure.security;

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
 * Filtro che intercetta le richieste HTTP e autentica l'utente tramite
 * un token JWT presente nell'header {@code Authorization} con schema
 * {@code Bearer}.
 *
 * <p>Esteende {@link OncePerRequestFilter} per garantire una singola
 * esecuzione per richiesta. In caso di token assente, la richiesta
 * prosegue nella catena di filtri senza autenticazione. Se il token è
 * presente ma non valido, la richiesta viene respinta con status 401.
 * In caso di token valido, il filtro popola il
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * con un {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
 * contenente il nome utente e le authorities estratte dal token.</p>
 *
 * @see JwtTokenValidator
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenValidator jwtTokenValidator;

    /**
     * Costruisce il filtro con il validatore di token JWT.
     *
     * @param jwtTokenValidator validatore per la verifica e il parsing dei
     *                          token JWT
     */
    public JwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    /**
     * Applica il filtro di autenticazione JWT alla richiesta corrente.
     *
     * <p>Se l'header {@code Authorization} non è presente o non inizia
     * con {@code Bearer }, la richiesta prosegue nella catena senza
     * autenticazione. Se il token è presente ma la validazione fallisce,
     * viene restituito uno status 401 (Unauthorized). Se il token è
     * valido, estrae il subject (nome utente) e le authorities, crea un
     * {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
     * e lo imposta nel {@link org.springframework.security.core.context.SecurityContextHolder}.</p>
     *
     * @param request     richiesta HTTP in ingresso
     * @param response    risposta HTTP in uscita
     * @param filterChain catena dei filtri successivi
     * @throws ServletException in caso di errore nella gestione della
     *                          richiesta
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

        Claims claims;
        try {
            claims = jwtTokenValidator.validateToken(token);
        } catch (Exception e) {
            // Token present but invalid — reject immediately with 401
            // (aligned with the central-system JwtAuthenticationFilter).
            log.warn("Rejecting request to {} — invalid or expired JWT token: {}",
                    request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT token");
            return;
        }

        String username = claims.getSubject();

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<SimpleGrantedAuthority> authorities = jwtTokenValidator.getAuthorities(claims);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username, null, authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated local user: {} with authorities: {}", username, authorities);
        }

        filterChain.doFilter(request, response);
    }
}

