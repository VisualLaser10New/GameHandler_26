package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocalSignupUserTest {

    private static final UserId USER_ID = new UserId("user-123");
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

    @Nested
    class Construction {

        @Test
        void shouldCreateLocalSignupUserSuccessfully() {
            List<String> roles = List.of("ROLE_USER");

            LocalSignupUser user = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", roles, NOW);

            assertThat(user.getUserId()).isEqualTo(USER_ID);
            assertThat(user.getUsername()).isEqualTo("john_doe");
            assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
            assertThat(user.getEmail()).isEqualTo("john@example.com");
            assertThat(user.getRoles()).containsExactly("ROLE_USER");
            assertThat(user.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        void shouldAcceptEmptyRolesList() {
            LocalSignupUser user = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", List.of(), NOW);
            assertThat(user.getRoles()).isEmpty();
        }

        @Test
        void shouldRejectAnyNullRequiredField() {
            assertThatThrownBy(() -> new LocalSignupUser(null, "u", "h", "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, null, "h", "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "  ", "h", "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", null, "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", "  ", "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", "h", null, List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", "h", "  ", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", "h", "e", null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "u", "h", "e", List.of("R"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectBlankUsernameOnlyWithWhitespace() {
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "\t \n", "h", "e", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RolesImmutability {

        @Test
        void shouldNotBeAffectedByMutationsOfTheOriginalList() {
            List<String> roles = new ArrayList<>();
            roles.add("ROLE_USER");

            LocalSignupUser user = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", roles, NOW);
            roles.add("ROLE_ADMIN");

            assertThat(user.getRoles()).containsExactly("ROLE_USER");
        }

        @Test
        void shouldReturnAnUnmodifiableRolesList() {
            LocalSignupUser user = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", List.of("ROLE_USER"), NOW);
            assertThatThrownBy(() -> user.getRoles().add("ROLE_ADMIN"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnAnUnmodifiableRolesListEvenWhenEmpty() {
            LocalSignupUser user = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", List.of(), NOW);
            assertThatThrownBy(() -> user.getRoles().add("ROLE_ADMIN"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class NullRoleElements {

        @Test
        void shouldRejectListContainingNullElementViaCopyOf() {
            List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER", null));
            assertThatThrownBy(() -> new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", roles, NOW))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Equality {

        @Test
        void localSignupUsersDoNotOverrideEqualsSoIdentityEqualityHolds() {
            LocalSignupUser a = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", List.of("R"), NOW);
            LocalSignupUser b = new LocalSignupUser(USER_ID, "john_doe", "hashed_password", "john@example.com", List.of("R"), NOW);
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
            assertThat(a.equals("not a user")).isFalse();
        }
    }
}
