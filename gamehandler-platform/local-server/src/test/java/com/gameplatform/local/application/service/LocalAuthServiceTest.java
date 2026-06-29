package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.exception.UserNotFoundException;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.local.infrastructure.security.JwtTokenProvider;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LoginResponseDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    private final java.time.Clock clock = java.time.Clock.systemUTC();

    LocalAuthService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new LocalAuthService(userRepository, jwtTokenProvider, clock);
    }

    private User userWithPassword(String password) {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        return new User(new UserId("u-1"), "alice", hash, List.of("PLAYER"), Instant.now());
    }

    @Test
    void shouldAuthenticateAndReturnToken() {
        User user = userWithPassword("password");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("jwt-token");

        LoginResponseDto response = service.authenticate("alice", "password");

        assertEquals("jwt-token", response.token());
        assertEquals("u-1", response.userId());
        // Updated behavior: service declares 1h expiration.
        assertTrue(response.expiresAt().isAfter(Instant.now().plus(50, ChronoUnit.MINUTES)));
        assertTrue(response.expiresAt().isBefore(Instant.now().plus(70, ChronoUnit.MINUTES)));
    }

    @Test
    void shouldFailWhenUserNotFound() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.authenticate("ghost", "pwd"));
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    void shouldFailWhenPasswordDoesNotMatch() {
        User user = userWithPassword("password");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class, () -> service.authenticate("alice", "wrong"));
        verify(jwtTokenProvider, never()).generateToken(any());
    }
}
