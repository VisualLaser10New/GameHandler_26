package com.gameplatform.central.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.UserRegisteredEventDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceFromSyncEdgeCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, outboxEventRepository, new ObjectMapper(), java.time.Clock.systemUTC());
    }

    private UserRegisteredEventDto dto(String userId, String username, String email, String hash, List<String> roles) {
        return new UserRegisteredEventDto(userId, username, email, hash, roles, Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("registerFromSync checks findById first and short-circuits when the userId already exists")
    void shouldCheckFindByIdFirst() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.of(new User(
                new UserId("u-1"), "alice", "h", "a@e.com", List.of("USER"), Instant.now())));

        userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER")));

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerFromSync short-circuits at findByUsername when userId is new but username exists")
    void shouldShortCircuitAtUsername() {
        when(userRepository.findById(new UserId("u-1"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new User(
                new UserId("other"), "alice", "h", "a@e.com", List.of("USER"), Instant.now())));

        userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER")));

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerFromSync preserves the original hashedPassword without re-hashing")
    void shouldPreserveHashedPassword() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.registerFromSync(dto("u-1", "alice", "a@e.com", "already-hashed", List.of("USER")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("already-hashed");
    }

    @Test
    @DisplayName("registerFromSync preserves the original createdAt from the dto (does not overwrite with Instant.now())")
    void shouldPreserveCreatedAt() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("registerFromSync does not emit an outbox event (unlike direct register())")
    void shouldNotEmitOutboxEvent() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER")));

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("EDGE-C1: registerFromSync with null email throws IllegalArgumentException (caught upstream by SyncReceiverService)")
    void nullEmailThrowsIllegalArgument() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerFromSync(dto("u-1", "alice", null, "h", List.of("USER"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EDGE-C2: registerFromSync with a blank role throws IllegalArgumentException")
    void blankRoleThrowsIllegalArgument() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER", " "))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EDGE-C3: registerFromSync with null roles throws IllegalArgumentException")
    void nullRolesThrowsIllegalArgument() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EDGE-C4: registerFromSync swallows DataIntegrityViolationException from concurrent save (no rethrow, but see BUG-C7 in analysis)")
    void swallowsDataIntegrityViolation() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() -> userService.registerFromSync(dto("u-1", "alice", "a@e.com", "h", List.of("USER"))))
                .doesNotThrowAnyException();
    }
}
