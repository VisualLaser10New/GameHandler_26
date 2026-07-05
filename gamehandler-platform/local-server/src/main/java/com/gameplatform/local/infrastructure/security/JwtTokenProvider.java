package com.gameplatform.local.infrastructure.security;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.TokenGeneratorPort;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
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
    @Deprecated(since = "B11", forRemoval = true)
    public String generateToken(User user, Instant now) {
        return generateTokenWithExpiry(user, now).token();
    }

    @Override
    public TokenWithExpiry generateTokenWithExpiry(User user, Instant now) {
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId().value())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new TokenWithExpiry(token, expiresAt);
    }

    public String generateToken(User user) {
        return generateToken(user, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }
}

