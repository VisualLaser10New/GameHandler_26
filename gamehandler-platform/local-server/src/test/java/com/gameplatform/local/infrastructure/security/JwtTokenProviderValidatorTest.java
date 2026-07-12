package com.gameplatform.local.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gameplatform.local.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;

class JwtTokenProviderValidatorTest {

    private static KeyPair keyPair;
    private static JwtTokenProvider provider;
    private static JwtTokenValidator validator;

    @BeforeAll
    static void setup() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        provider = new JwtTokenProvider(keyPair.getPrivate());
        validator = new JwtTokenValidator(keyPair.getPublic());
    }

    private User sampleUser() {
        return new User(new com.gameplatform.shared.domain.model.UserId("u1"), "alice", "hash",
                List.of("USER", "ROLE_ADMIN"), Instant.now());
    }

    @Test
    void generateAndValidateRoundTrip() {
        String token = provider.generateToken(sampleUser());
        Claims claims = validator.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId")).isEqualTo("u1");
        assertThat(claims.get("roles")).isEqualTo(List.of("USER", "ROLE_ADMIN"));
    }

    @Test
    void tokenExpiresInAboutOneHour() {
        String token = provider.generateToken(sampleUser());
        Claims claims = validator.validateToken(token);
        Instant exp = claims.getExpiration().toInstant();
        Instant now = Instant.now();
        long seconds = Math.abs(now.until(exp, ChronoUnit.SECONDS));
        assertThat(seconds).isBetween(3500L, 3600L);
    }

    @Test
    void expiredTokenThrowsExpiredJwt() {
        String expired = Jwts.builder()
                .subject("alice")
                .claim("userId", "u1")
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatThrownBy(() -> validator.validateToken(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void malformedTokenThrowsMalformedJwt() {
        assertThatThrownBy(() -> validator.validateToken("not.a.jwt")).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair other = kpg.generateKeyPair();
        String token = Jwts.builder().subject("x").signWith(other.getPrivate(), Jwts.SIG.RS256).compact();
        assertThatThrownBy(() -> validator.validateToken(token))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void getAuthoritiesPrefixesRoleWhenMissing() {
        String token = provider.generateToken(sampleUser()); // roles: USER, ROLE_ADMIN
        Claims claims = validator.validateToken(token);
        var authorities = validator.getAuthorities(claims);
        assertThat(authorities).extracting("authority").contains("ROLE_PLAYER", "ROLE_PLATFORM_ADMIN");
    }

    @Test
    void getAuthoritiesWithNullRolesReturnsEmpty() {
        String token = Jwts.builder().subject("x").signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
        Claims claims = validator.validateToken(token);
        assertThat(validator.getAuthorities(claims)).isEmpty();
    }
}
