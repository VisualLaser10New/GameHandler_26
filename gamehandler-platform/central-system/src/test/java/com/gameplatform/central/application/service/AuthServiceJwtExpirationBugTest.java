package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LoginResponseDto;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceJwtExpirationBugTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FailedLoginAttemptRepository failedLoginAttemptRepository;

    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                new DefaultResourceLoader(),
                "classpath:alt-private.pem",
                Duration.ofMinutes(1).toMillis()
        );
        jwtTokenProvider.init();
        authService = new AuthService(userRepository, failedLoginAttemptRepository, jwtTokenProvider, Clock.systemUTC());
        when(failedLoginAttemptRepository.countFailedAttempts(eq("alice"), any(Instant.class))).thenReturn(0L);
    }

    @Test
    @DisplayName("BUG-AUTH-01: LoginResponseDto.expiresAt must match the actual JWT exp claim")
    void authenticate_reportsSameExpirationAsGeneratedJwt() {
        String password = "correct-password";
        User user = new User(
                new UserId("user-1"),
                "alice",
                BCrypt.hashpw(password, BCrypt.gensalt()),
                "alice@example.com",
                List.of("USER"),
                Instant.now()
        );
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        LoginResponseDto response = authService.authenticate("alice", password);
        Claims claims = jwtTokenProvider.getClaims(response.token());
        Date tokenExpiration = claims.getExpiration();

        assertThat(response.expiresAt())
                .as("The response expiration should describe the token that was actually issued")
                .isCloseTo(tokenExpiration.toInstant(), within(5, java.time.temporal.ChronoUnit.SECONDS));
    }
}
