package com.gameplatform.central.domain.model;

import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void shouldCreateUserSuccessfullyWhenInputsAreValid() {
        UserId id = new UserId("user-1");
        List<String> roles = List.of("ADMIN", "USER");
        Instant now = Instant.now();

        User user = new User(id, "john_doe", "hashed_pwd", "john@example.com", roles, now);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("john_doe");
        assertThat(user.getPasswordHash()).isEqualTo("hashed_pwd");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getRoles()).containsExactly("ADMIN", "USER");
        assertThat(user.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void shouldThrowExceptionWhenConstructorInputsAreInvalid() {
        UserId validId = new UserId("user-1");
        List<String> validRoles = List.of("USER");
        Instant validTime = Instant.now();

        // Null id
        assertThatThrownBy(() -> new User(null, "username", "pwd", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank username
        assertThatThrownBy(() -> new User(validId, null, "pwd", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "", "pwd", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "   ", "pwd", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank password hash
        assertThatThrownBy(() -> new User(validId, "username", null, "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "username", "", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "username", "   ", "email@test.com", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank email
        assertThatThrownBy(() -> new User(validId, "username", "pwd", null, validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "username", "pwd", "", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "username", "pwd", "   ", validRoles, validTime))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/invalid roles
        assertThatThrownBy(() -> new User(validId, "username", "pwd", "email@test.com", null, validTime))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new User(validId, "username", "pwd", "email@test.com", List.of("USER", ""), validTime))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        assertThatThrownBy(() -> new User(validId, "username", "pwd", "email@test.com", listWithNull, validTime))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefensivelyCopyRolesInConstructor() {
        UserId id = new UserId("user-1");
        List<String> roles = new ArrayList<>();
        roles.add("USER");
        roles.add("ADMIN");

        User user = new User(id, "john_doe", "hashed_pwd", "john@example.com", roles, Instant.now());
        roles.add("SUPER_ADMIN"); // Modify original list

        assertThat(user.getRoles()).containsExactly("USER", "ADMIN");
    }

    @Test
    void shouldReturnUnmodifiableRolesList() {
        UserId id = new UserId("user-1");
        User user = new User(id, "john_doe", "hashed_pwd", "john@example.com", List.of("USER"), Instant.now());

        assertThatThrownBy(() -> user.getRoles().add("ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldChangePasswordSuccessfullyAndValidateInputs() {
        UserId id = new UserId("user-1");
        User user = new User(id, "john_doe", "hashed_pwd", "john@example.com", List.of("USER"), Instant.now());

        user.changePassword("new_pwd");
        assertThat(user.getPasswordHash()).isEqualTo("new_pwd");

        assertThatThrownBy(() -> user.changePassword(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changePassword(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changePassword("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUpdateRolesSuccessfullyAndValidateInputs() {
        UserId id = new UserId("user-1");
        User user = new User(id, "john_doe", "hashed_pwd", "john@example.com", List.of("USER"), Instant.now());

        user.updateRoles(List.of("ADMIN"));
        assertThat(user.getRoles()).containsExactly("ADMIN");

        assertThatThrownBy(() -> user.updateRoles(null))
                .isInstanceOf(IllegalArgumentException.class);

        List<String> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        assertThatThrownBy(() -> user.updateRoles(listWithNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        UserId id1 = new UserId("user-1");
        UserId id2 = new UserId("user-1");
        UserId id3 = new UserId("user-2");

        User user1 = new User(id1, "john_doe", "pwd", "john@example.com", List.of("USER"), Instant.now());
        User user2 = new User(id2, "john_doe2", "pwd2", "john2@example.com", List.of("USER"), Instant.now());
        User user3 = new User(id3, "john_doe", "pwd", "john@example.com", List.of("USER"), Instant.now());

        assertThat(user1).isEqualTo(user2);
        assertThat(user1).isNotEqualTo(user3);
        assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
        assertThat(user1.hashCode()).isNotEqualTo(user3.hashCode());
    }
}
