package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.exception.UserNotFoundException;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.AuthenticateLocalUserUseCase;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LocalAuthService implements AuthenticateLocalUserUseCase {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public LocalAuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public LoginResponseDto authenticate(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user);
        
        // Default token expiration of 1 hour
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        return new LoginResponseDto(token, user.getUserId().value(), expiresAt);
    }
}
