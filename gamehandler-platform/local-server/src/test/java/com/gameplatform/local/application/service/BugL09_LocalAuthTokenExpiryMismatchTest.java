package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LoginResponseDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug L-09: LocalAuthService exposes expiresAt using the injected Clock, but
 * JwtTokenProvider signs the JWT using Instant.now() directly. The client can receive
 * an expiresAt value that does not match the actual JWT exp claim.
 */
class BugL09_LocalAuthTokenExpiryMismatchTest {

    @Test
    @DisplayName("BUG L-09: login response expiresAt must match JWT exp claim")
    void loginResponseExpiryShouldMatchJwtExpiryClaim() throws Exception {
        Instant fixedNow = Instant.parse("2035-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        UserRepository userRepository = mock(UserRepository.class);
        User user = new User(
                new UserId("user-1"),
                "alice",
                BCrypt.hashpw("secret", BCrypt.gensalt()),
                List.of("USER"),
                fixedNow
        );
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        LocalAuthService authService = new LocalAuthService(
                userRepository,
                new JwtTokenProvider(keyPair.getPrivate()),
                fixedClock
        );

        LoginResponseDto response = authService.authenticate("alice", "secret");
        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(response.token())
                .getPayload();

        assertEquals(response.expiresAt(), claims.getExpiration().toInstant(),
                "The advertised LoginResponseDto.expiresAt must be the same instant encoded in the JWT exp claim.");
    }
}
