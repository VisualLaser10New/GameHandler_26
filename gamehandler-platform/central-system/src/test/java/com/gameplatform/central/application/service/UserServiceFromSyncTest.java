package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceFromSyncTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, outboxEventRepository, new ObjectMapper());
    }

    @Test
    void registerFromSync_shouldCreateUser_whenNoConflicts() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.registerFromSync(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getId().value()).isEqualTo("user-123");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed_pw");
        assertThat(saved.getRoles()).containsExactly("USER");

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenUserIdAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123")))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenUsernameAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void registerFromSync_shouldSkip_whenEmailAlreadyExists() {
        UserRegisteredEventDto dto = new UserRegisteredEventDto(
                "user-123", "alice", "alice@example.com", "hashed_pw", List.of("USER"), Instant.now()
        );
        when(userRepository.findById(new UserId("user-123"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(mock(User.class)));

        userService.registerFromSync(dto);

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
