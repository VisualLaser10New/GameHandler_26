package com.gameplatform.local.infrastructure.security;

import com.gameplatform.shared.domain.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.PublicKey;
import java.util.List;

public class JwtTokenValidator {

    private final PublicKey publicKey;

    public JwtTokenValidator(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

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

