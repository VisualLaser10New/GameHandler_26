package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.domain.ports.out.TokenProviderPort;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.security.Role;
import com.gameplatform.shared.domain.security.TokenWithExpiry;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}, covering:
 * <ul>
 *   <li>Rate limiting: 5 failed attempts block the 6th within 60 s</li>
 *   <li>Timing-attack prevention: dummy BCrypt check executed for unknown users</li>
 *   <li>Successful authentication returns a {@link LoginResponseDto}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FailedLoginAttemptRepository failedLoginAttemptRepository;

    @Mock
    private TokenProviderPort jwtTokenProvider;

    private AuthService authService;
    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, failedLoginAttemptRepository, jwtTokenProvider, clock);
        lenient().when(failedLoginAttemptRepository.countFailedAttempts(anyString(), any())).thenReturn(0L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Successful authentication
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticate_shouldReturnLoginResponse_whenCredentialsAreValid() {
        String plainPassword = "correctPassword";
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        User user = buildUser("alice", hash);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateTokenWithExpiry(eq(user), any(Instant.class)))
                .thenReturn(new TokenWithExpiry("mock-jwt-token", Instant.now().plusSeconds(60)));

        LoginResponseDto response = authService.authenticate("alice", plainPassword);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock-jwt-token");
    }

    @Test
    void authenticate_shouldPropagatePlayerRoleIntoToken() {
        String plainPassword = "correctPassword";
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        User player = new User(
                new UserId(java.util.UUID.randomUUID().toString()),
                "dave",
                hash,
                "dave@example.com",
                List.of(Role.PLAYER.name()),
                Instant.now()
        );

        when(userRepository.findByUsername("dave")).thenReturn(Optional.of(player));
        when(jwtTokenProvider.generateTokenWithExpiry(any(User.class), any(Instant.class)))
                .thenReturn(new TokenWithExpiry("mock-jwt-token", Instant.now().plusSeconds(60)));

        authService.authenticate("dave", plainPassword);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(jwtTokenProvider).generateTokenWithExpiry(captor.capture(), any(Instant.class));
        assertThat(captor.getValue().getRoles()).containsExactly(Role.PLAYER.name());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Unknown user – timing-attack prevention
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticate_shouldThrowInvalidCredentials_whenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // Must throw InvalidCredentialsException even if the user is absent
        assertThatThrownBy(() -> authService.authenticate("ghost", "anything"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    /**
     * Verifies that the timing-attack dummy hash constant in AuthService is a syntactically
     * valid BCrypt hash so BCrypt.checkpw doesn't throw when user is not found.
     * (We cannot easily measure wall-clock time in a unit test, but we can verify the path
     * runs without error – any exception would indicate the dummy hash is malformed.)
     */
    @Test
    void authenticate_shouldNotThrowNpeOrBCryptError_forUnknownUserTimingPath() {
        when(userRepository.findByUsername("unknownUser")).thenReturn(Optional.empty());

        // Should throw InvalidCredentialsException (not NPE / BCrypt parse error)
        assertThatThrownBy(() -> authService.authenticate("unknownUser", "somePassword"))
                .isInstanceOf(InvalidCredentialsException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Wrong password
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticate_shouldThrowInvalidCredentials_whenPasswordIsWrong() {
        String hash = BCrypt.hashpw("correctPassword", BCrypt.gensalt());
        User user = buildUser("alice", hash);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("alice", "wrongPassword"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rate limiting
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticate_shouldBlockOnSixthAttempt_afterFiveFailedAttemptsWithinWindow() {
        // All attempts for an unknown user – 5 should go through, the 6th must be rate-limited
        when(userRepository.findByUsername("eve")).thenReturn(Optional.empty());
        when(failedLoginAttemptRepository.countFailedAttempts(eq("eve"), any()))
                .thenReturn(0L, 1L, 2L, 3L, 4L, 5L);

        for (int i = 1; i <= 5; i++) {
            final int attempt = i;
            assertThatThrownBy(() -> authService.authenticate("eve", "wrong"))
                    .as("Attempt %d should throw InvalidCredentialsException (user not found)", attempt)
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // 6th attempt – must be rate-limited BEFORE user lookup (429, not 401)
        assertThatThrownBy(() -> authService.authenticate("eve", "wrong"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many failed login attempts");
    }

    @Test
    void authenticate_shouldTrackRateLimitPerUsername_independently() {
        // "alice" has 5 failed attempts; "bob" should still be allowed
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(failedLoginAttemptRepository.countFailedAttempts(eq("alice"), any()))
                .thenReturn(0L, 1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            try { authService.authenticate("alice", "bad"); } catch (InvalidCredentialsException ignored) {}
        }

        // alice is now rate-limited → RateLimitExceededException (429)
        assertThatThrownBy(() -> authService.authenticate("alice", "bad"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many failed login attempts");

        // bob is on a separate counter and must not be affected
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        when(failedLoginAttemptRepository.countFailedAttempts(eq("bob"), any())).thenReturn(0L);
        assertThatThrownBy(() -> authService.authenticate("bob", "bad"))
                .isInstanceOf(InvalidCredentialsException.class)
                // should NOT be a rate-limit message
                .doesNotHave(new org.assertj.core.api.Condition<>(
                        t -> t.getMessage() != null && t.getMessage().contains("Too many failed"),
                        "rate-limit message"
                ));
    }

    @Test
    void authenticate_shouldNotApplyRateLimit_afterSuccessfulLogin() {
        String plainPassword = "correct";
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        User user = buildUser("charlie", hash);

        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateTokenWithExpiry(eq(user), any(Instant.class)))
                .thenReturn(new TokenWithExpiry("jwt", Instant.now().plusSeconds(60)));

        // Successful login must not record a failure
        authService.authenticate("charlie", plainPassword);
        authService.authenticate("charlie", plainPassword);
        authService.authenticate("charlie", plainPassword);
        authService.authenticate("charlie", plainPassword);
        authService.authenticate("charlie", plainPassword);

        // 5 successful logins → still no rate limit
        LoginResponseDto response = authService.authenticate("charlie", plainPassword);
        assertThat(response.token()).isEqualTo("jwt");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private User buildUser(String username, String passwordHash) {
        return new User(
                new UserId(java.util.UUID.randomUUID().toString()),
                username,
                passwordHash,
                username + "@example.com",
                List.of("USER"),
                Instant.now()
        );
    }
}
