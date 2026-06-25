package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class JwtTokenProvider {

    private final PrivateKey privateKey;

    public JwtTokenProvider(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId().value())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}

