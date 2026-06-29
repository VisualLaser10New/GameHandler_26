package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.UserAlreadyExistsException;
import com.gameplatform.central.domain.exception.UserNotFoundException;
import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}, covering TOCTOU handling via
 * DataIntegrityViolationException, additive role merging, and UserNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, outboxEventRepository, new ObjectMapper());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_shouldReturnSavedUser_whenInputIsValid() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.register("alice", "s3cr3t", "alice@example.com");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRoles()).containsExactly("USER");
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void register_shouldThrowUserAlreadyExistsException_whenUsernameAlreadyTaken() {
        User existing = buildUser("alice", List.of("USER"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.register("alice", "password", "other@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowUserAlreadyExistsException_whenEmailAlreadyTaken() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());
        User existing = buildUser("alice", List.of("USER"));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.register("bob", "password", "alice@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    /**
     * TOCTOU scenario: two concurrent registrations pass the initial uniqueness check,
     * but one loses the database race.  The {@link DataIntegrityViolationException} thrown
     * by the DB must be caught and converted to {@link UserAlreadyExistsException}.
     */
    @Test
    void register_shouldThrowUserAlreadyExistsException_onDataIntegrityViolation_TOCTOU() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        // Simulate concurrent insert winning the race → DB raises unique constraint violation
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> userService.register("alice", "password", "alice@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("User already exists");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // updateUser()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void updateUser_shouldThrowUserNotFoundException_whenUserDoesNotExist() {
        UserId id = new UserId("unknown-id");
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(id, null, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUser_shouldOverwriteRoles_notMerge() {
        UserId id = new UserId("user-1");
        User existing = buildUser("alice", List.of("USER"));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Setting roles to [ADMIN] for a user that already has USER → roles must be overwritten to just ADMIN
        User updated = userService.updateUser(id, null, List.of("ADMIN"));

        assertThat(updated.getRoles())
                .containsExactly("ADMIN");
    }

    @Test
    void updateUser_shouldDeduplicateNewRoles_whenDuplicateRolesProvided() {
        UserId id = new UserId("user-1");
        User existing = buildUser("alice", List.of("USER", "ADMIN"));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Providing a roles list with duplicates should result in unique roles
        User updated = userService.updateUser(id, null, List.of("USER", "USER"));

        assertThat(updated.getRoles()).containsExactly("USER");
    }

    @Test
    void updateUser_shouldChangePassword_whenNewPasswordProvided() {
        UserId id = new UserId("user-1");
        User existing = buildUser("alice", List.of("USER"));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(id, "newPassword123", null);

        User saved = captor.getValue();
        // Password must have been re-hashed (BCrypt hash starts with $2a$)
        assertThat(saved.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    void updateUser_shouldPublishOutboxEvent_onSuccessfulUpdate() {
        UserId id = new UserId("user-1");
        User existing = buildUser("alice", List.of("USER"));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(outboxEventRepository.save(eventCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(id, null, List.of("ADMIN"));

        OutboxEvent published = eventCaptor.getValue();
        assertThat(published.getEventType()).isEqualTo("USER_UPDATED");
        assertThat(published.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getAllUsersForSync()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getAllUsersForSync_shouldReturnDtoForEachUser() {
        User u1 = buildUser("alice", List.of("USER"));
        User u2 = buildUser("bob", List.of("ADMIN"));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        var dtos = userService.getAllUsersForSync();

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(dto -> dto.username())
                .containsExactlyInAnyOrder("alice", "bob");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private User buildUser(String username, List<String> roles) {
        return new User(
                new UserId(java.util.UUID.randomUUID().toString()),
                username,
                "$2a$10$fakehashedpassword123456789012345678901234567890",
                username + "@example.com",
                roles,
                Instant.now()
        );
    }
}
