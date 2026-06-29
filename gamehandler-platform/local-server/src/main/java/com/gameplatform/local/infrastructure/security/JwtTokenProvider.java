package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.TokenGeneratorPort;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class JwtTokenProvider implements TokenGeneratorPort {

    private final PrivateKey privateKey;
    private final Clock clock;

    public JwtTokenProvider(PrivateKey privateKey, Clock clock) {
        this.privateKey = privateKey;
        this.clock = clock;
    }

    // Backward compatibility constructor
    public JwtTokenProvider(PrivateKey privateKey) {
        this(privateKey, Clock.systemUTC());
    }

    @Override
    public String generateToken(User user, Instant now) {
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

    public String generateToken(User user) {
        return generateToken(user, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }
}

