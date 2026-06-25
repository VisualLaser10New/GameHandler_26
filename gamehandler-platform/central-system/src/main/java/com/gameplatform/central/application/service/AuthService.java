package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.exception.InvalidCredentialsException;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.in.AuthenticateUserUseCase;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.central.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.dto.LoginResponseDto;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService implements AuthenticateUserUseCase {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public LoginResponseDto authenticate(String username, String password) {
        InvalidCredentialsException excptn = new InvalidCredentialsException("Invalid username or password");
        User utente = userRepository.findByUsername(username).orElseThrow(() -> excptn);

        if (BCrypt.checkpw(password, utente.getPasswordHash())) {
            return new LoginResponseDto(jwtTokenProvider.generateToken(utente), utente.getId().value(), Instant.now().plus(24, ChronoUnit.HOURS));
        } else {
            throw excptn;
        }
    }
}
