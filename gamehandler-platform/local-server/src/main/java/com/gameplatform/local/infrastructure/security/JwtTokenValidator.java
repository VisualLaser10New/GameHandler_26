package com.gameplatform.local.infrastructure.security;

import com.gameplatform.shared.domain.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.PublicKey;
import java.util.List;

/**
 * Validatore di token JWT che verifica la firma e restituisce i claims
 * contenuti nel token.
 *
 * <p>Utilizza una chiave pubblica per la verifica della firma RSA con
 * algoritmo RS256. Fornisce inoltre un metodo per estrarre le authorities
 * (ruoli) dai claims del token sotto forma di
 * {@link org.springframework.security.core.authority.SimpleGrantedAuthority}.</p>
 *
 * @see JwtTokenProvider
 * @see JwtAuthenticationFilter
 */
public class JwtTokenValidator {

    private final PublicKey publicKey;

    /**
     * Costruisce un validatore con la chiave pubblica specificata.
     *
     * @param publicKey chiave pubblica per la verifica della firma dei
     *                  token JWT
     */
    public JwtTokenValidator(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Valida un token JWT e restituisce i claims in esso contenuti.
     *
     * <p>Verifica la firma del token utilizzando la chiave pubblica
     * configurata con l'algoritmo RS256. Se la firma non è valida o
     * il token è scaduto, viene sollevata un'eccezione.</p>
     *
     * @param token token JWT da validare
     * @return claims contenuti nel token
     * @throws io.jsonwebtoken.JwtException se il token non è valido,
     *         scaduto o la firma non corrisponde
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Estrae la lista delle authorities (ruoli) dai claims del token JWT.
     *
     * <p>Legge il claim {@code roles} dalla lista dei ruoli e li
     * converte in oggetti {@link SimpleGrantedAuthority} tramite il
     * formato definito da {@link com.gameplatform.shared.domain.security.Role#toAuthorityNames}.</p>
     *
     * @param claims claims estratti da un token JWT validato
     * @return lista delle authorities corrispondenti ai ruoli, vuota se
     *         il claim {@code roles} non è presente
     */
    @SuppressWarnings("unchecked")
    public List<SimpleGrantedAuthority> getAuthorities(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return Role.toAuthorityNames(roles)
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}

