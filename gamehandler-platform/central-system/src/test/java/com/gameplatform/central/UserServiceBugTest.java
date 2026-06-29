package com.gameplatform.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.application.service.UserService;
import com.gameplatform.central.domain.model.User;
import com.gameplatform.central.domain.ports.out.OutboxEventRepository;
import com.gameplatform.central.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug C-02: UserService.updateUser() merges roles instead of replacing them.
 * Lines 84-91 build a mergedRoles list starting from existing roles and only ADD new ones.
 * It never removes existing roles, so calling updateUser with a subset of roles
 * still retains the old roles.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBugTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userService = new UserService(userRepository, outboxEventRepository, objectMapper);
    }

    @Test
    @DisplayName("C-02: updateUser with newRoles=[USER] on a [USER, ADMIN] user should replace roles")
    void updateUser_rolesAreReplaced() {
        // Arrange: existing user with both USER and ADMIN roles
        UserId userId = new UserId("user-123");
        User existingUser = new User(
                userId,
                "testuser",
                "$2a$10$hashedPasswordPlaceholder1234",
                "test@example.com",
                List.of("USER", "ADMIN"),
                Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        // save() returns the same user it receives
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // outbox save returns any passed event
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: update with newRoles=[USER] only — intending to REMOVE the ADMIN role
        User updatedUser = userService.updateUser(userId, null, List.of("USER"));

        // Capture what was actually saved to verify the persisted state
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        List<String> savedRoles = userCaptor.getValue().getRoles();

        assertFalse(savedRoles.contains("ADMIN"), "ADMIN role should have been removed");
        assertTrue(savedRoles.contains("USER"), "USER role should be present");
        assertEquals(1, savedRoles.size(), "Roles should have exactly 1 entry");
        assertEquals(List.of("USER"), savedRoles, "Roles should be exactly [USER] after update");
    }

    @Test
    @DisplayName("C-02: updateUser can remove a role — updating with [USER] removes MODERATOR")
    void updateUser_removesRoleSuccessfully() {
        // Arrange: user starts with [USER, MODERATOR]
        UserId userId = new UserId("user-456");
        User existingUser = new User(
                userId,
                "anotheruser",
                "$2a$10$hashedPasswordPlaceholder5678",
                "another@example.com",
                List.of("USER", "MODERATOR"),
                Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: try to set roles to just [USER], effectively removing MODERATOR
        User updatedUser = userService.updateUser(userId, null, List.of("USER"));

        List<String> resultRoles = updatedUser.getRoles();

        assertFalse(resultRoles.contains("MODERATOR"), "MODERATOR role should have been removed");
        assertEquals(1, resultRoles.size(), "Result should have 1 role");
        assertEquals(List.of("USER"), resultRoles, "Roles should be exactly [USER] after update");
    }

    @Test
    @DisplayName("C-02: updateUser with entirely different role set overwrites all old roles")
    void updateUser_entirelyNewRoleSet_overwritesOldRoles() {
        // Arrange: user has [USER]
        UserId userId = new UserId("user-789");
        User existingUser = new User(
                userId,
                "roleswapuser",
                "$2a$10$hashedPasswordPlaceholderABCD",
                "swap@example.com",
                List.of("USER"),
                Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: try to replace roles with just [ADMIN] — user should no longer have USER
        User updatedUser = userService.updateUser(userId, null, List.of("ADMIN"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        List<String> savedRoles = userCaptor.getValue().getRoles();

        assertFalse(savedRoles.contains("USER"), "USER role should have been removed");
        assertTrue(savedRoles.contains("ADMIN"), "ADMIN role should have been added");
        assertEquals(1, savedRoles.size(), "Roles should have exactly 1 entry [ADMIN]");
    }
}
