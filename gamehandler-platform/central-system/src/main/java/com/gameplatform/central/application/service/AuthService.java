package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.exception.RateLimitExceededException;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService implements AuthenticateUserUseCase {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_HASH = "$2a$10$S9dK/n/rP.qZ9H9yK3m/Vu1YV7k4m4k5m6m7m8m9m0m1m2m3m4m5m";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ConcurrentHashMap<String, List<Instant>> failedAttempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public LoginResponseDto authenticate(String username, String password) {
        checkRateLimit(username);

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (BCrypt.checkpw(password, user.getPasswordHash())) {
                return new LoginResponseDto(jwtTokenProvider.generateToken(user), user.getId().value(), Instant.now().plus(24, ChronoUnit.HOURS));
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
        List<Instant> attempts = failedAttempts.computeIfAbsent(username, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (attempts) {
            Instant now = Instant.now();
            attempts.removeIf(instant -> instant.isBefore(now.minusSeconds(60)));
            if (attempts.size() >= 5) {
                log.warn("Rate limit blocked: Username '{}' has had {} failed login attempts in the last 60 seconds.", username, attempts.size());
                throw new RateLimitExceededException("Too many failed login attempts. Please try again later.");
            }
        }
    }

    private void recordFailure(String username) {
        List<Instant> attempts = failedAttempts.computeIfAbsent(username, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (attempts) {
            attempts.add(Instant.now());
        }
    }
}
