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

class UserTest {

    private static final UserId USER_ID = new UserId("user-123");
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

    @Nested
    class Construction {

        @Test
        void shouldCreateUserSuccessfully() {
            List<String> roles = List.of("ROLE_USER");

            User user = new User(USER_ID, "john_doe", "hashed_password", roles, NOW);

            assertThat(user.getUserId()).isEqualTo(USER_ID);
            assertThat(user.getUsername()).isEqualTo("john_doe");
            assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
            assertThat(user.getRoles()).containsExactly("ROLE_USER");
            assertThat(user.getSyncedAt()).isEqualTo(NOW);
        }

        @Test
        void shouldAcceptEmptyRolesList() {
            User user = new User(USER_ID, "john_doe", "hashed_password", List.of(), NOW);
            assertThat(user.getRoles()).isEmpty();
        }

        @Test
        void shouldRejectAnyNullRequiredField() {
            assertThatThrownBy(() -> new User(null, "u", "h", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, null, "h", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, "  ", "h", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, "u", null, List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, "u", "  ", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, "u", "h", null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new User(USER_ID, "u", "h", List.of("R"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectBlankUsernameOnlyWithWhitespace() {
            assertThatThrownBy(() -> new User(USER_ID, "\t \n", "h", List.of("R"), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectNullUserIdValueEagerly() {
            assertThatThrownBy(() -> new UserId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RolesImmutability {

        @Test
        void shouldNotBeAffectedByMutationsOfTheOriginalList() {
            List<String> roles = new ArrayList<>();
            roles.add("ROLE_USER");

            User user = new User(USER_ID, "john_doe", "hashed_password", roles, NOW);
            roles.add("ROLE_ADMIN");

            assertThat(user.getRoles()).containsExactly("ROLE_USER");
        }

        @Test
        void shouldReturnAnUnmodifiableRolesList() {
            User user = new User(USER_ID, "john_doe", "hashed_password", List.of("ROLE_USER"), NOW);
            assertThatThrownBy(() -> user.getRoles().add("ROLE_ADMIN"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnAnUnmodifiableRolesListEvenWhenEmpty() {
            User user = new User(USER_ID, "john_doe", "hashed_password", List.of(), NOW);
            assertThatThrownBy(() -> user.getRoles().add("ROLE_ADMIN"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnAnUnmodifiableRolesListWhenBackedByArrayList() {
            List<String> roles = new ArrayList<>();
            roles.add("ROLE_USER");
            User user = new User(USER_ID, "john_doe", "hashed_password", roles, NOW);
            assertThatThrownBy(() -> user.getRoles().add("ROLE_ADMIN"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldPreserveDuplicateRolesAsProvided() {
            User user = new User(USER_ID, "john_doe", "hashed_password",
                    List.of("ROLE_USER", "ROLE_USER"), NOW);
            assertThat(user.getRoles()).hasSize(2);
        }

        @Test
        void shouldPreserveRoleOrdering() {
            User user = new User(USER_ID, "john_doe", "hashed_password",
                    List.of("ROLE_A", "ROLE_B", "ROLE_C"), NOW);
            assertThat(user.getRoles()).containsExactly("ROLE_A", "ROLE_B", "ROLE_C");
        }
    }

    @Nested
    class NullRoleElements {

        @Test
        void shouldRejectListContainingNullElementViaCopyOf() {
            List<String> roles = new ArrayList<>(Arrays.asList("ROLE_USER", null));
            assertThatThrownBy(() -> new User(USER_ID, "john_doe", "hashed_password", roles, NOW))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectListWithSingleNullElement() {
            List<String> roles = new ArrayList<>();
            roles.add(null);
            assertThatThrownBy(() -> new User(USER_ID, "john_doe", "hashed_password", roles, NOW))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Equality {

        @Test
        void usersDoNotOverrideEqualsSoIdentityEqualityHolds() {
            User a = new User(USER_ID, "john_doe", "hashed_password", List.of("R"), NOW);
            User b = new User(USER_ID, "john_doe", "hashed_password", List.of("R"), NOW);
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
            assertThat(a.equals("not a user")).isFalse();
        }
    }
}
