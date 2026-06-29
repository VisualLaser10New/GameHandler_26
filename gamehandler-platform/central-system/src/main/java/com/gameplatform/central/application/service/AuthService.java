package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.model.FailedLoginAttempt;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.central.domain.ports.out.FailedLoginAttemptRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AuthService implements AuthenticateUserUseCase {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_HASH = "$2a$10$LwY.F2hWpSXe/9jG4rXf6OQk3V0vYhZ8S.a8m3uW9Ym8X6n3uS3lO";

    private final UserRepository userRepository;
    private final FailedLoginAttemptRepository failedLoginAttemptRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            FailedLoginAttemptRepository failedLoginAttemptRepository,
            JwtTokenProvider jwtTokenProvider,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.failedLoginAttemptRepository = failedLoginAttemptRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.clock = clock;
    }

    @Override
    public LoginResponseDto authenticate(String username, String password) {
        checkRateLimit(username);

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (BCrypt.checkpw(password, user.getPasswordHash())) {
                return new LoginResponseDto(jwtTokenProvider.generateToken(user), user.getId().value(), Instant.now(clock).plus(24, ChronoUnit.HOURS));
            } else {
                recordFailure(username);
                log.warn("Failed login attempt: Incorrect password for username '{}'", username);
                throw new InvalidCredentialsException("Invalid username or password");
            }
        } else {
            // Compute dummy BCrypt check to prevent timing attacks
            BCrypt.checkpw(password, DUMMY_HASH);
            recordFailure(username);
            log.warn("Failed login attempt: User not found for username '{}'", username);
            throw new InvalidCredentialsException("Invalid username or password");
        }
    }

    private void checkRateLimit(String username) {
        Instant since = Instant.now(clock).minusSeconds(60);
        long failures = failedLoginAttemptRepository.countFailedAttempts(username, since);
        if (failures >= 5) {
            log.warn("Rate limit blocked: Username '{}' has had {} failed login attempts in the last 60 seconds.", username, failures);
            throw new RateLimitExceededException("Too many failed login attempts. Please try again later.");
        }
    }

    private void recordFailure(String username) {
        FailedLoginAttempt attempt = new FailedLoginAttempt(username, Instant.now(clock));
        failedLoginAttemptRepository.save(attempt);
    }
}
