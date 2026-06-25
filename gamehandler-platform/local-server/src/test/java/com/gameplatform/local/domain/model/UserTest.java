package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void shouldCreateUserSuccessfully() {
        UserId userId = new UserId("user-123");
        List<String> roles = List.of("ROLE_USER");
        Instant now = Instant.now();

        User user = new User(userId, "john_doe", "hashed_password", roles, now);

        assertEquals(userId, user.getUserId());
        assertEquals("john_doe", user.getUsername());
        assertEquals("hashed_password", user.getPasswordHash());
        assertEquals(roles, user.getRoles());
        assertEquals(now, user.getSyncedAt());
    }

    @Test
    void shouldThrowExceptionWhenRequiredFieldsAreNullOrEmpty() {
        UserId userId = new UserId("user-123");
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> new User(null, "user", "hash", List.of("ROLE_USER"), now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, null, "hash", List.of("ROLE_USER"), now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, " ", "hash", List.of("ROLE_USER"), now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, "user", null, List.of("ROLE_USER"), now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, "user", " ", List.of("ROLE_USER"), now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, "user", "hash", null, now));
        assertThrows(IllegalArgumentException.class, () -> new User(userId, "user", "hash", List.of("ROLE_USER"), null));
    }

    @Test
    void shouldMakeRolesImmutable() {
        UserId userId = new UserId("user-123");
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");

        User user = new User(userId, "john_doe", "hashed_password", roles, Instant.now());

        // Mutate original list
        roles.add("ROLE_ADMIN");

        // User roles list should remain unchanged
        assertEquals(1, user.getRoles().size());
        assertEquals("ROLE_USER", user.getRoles().get(0));

        // Try modifying user roles directly (should throw UnsupportedOperationException)
        assertThrows(UnsupportedOperationException.class, () -> user.getRoles().add("ROLE_ADMIN"));
    }
}
