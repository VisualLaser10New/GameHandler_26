package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserMapperEdgeCaseTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    @DisplayName("toDomainFromLocalUser maps id, username, passwordHash and createdAt->syncedAt")
    void toDomainFromLocalUserMapsFields() {
        Instant t = Instant.parse("2026-06-25T10:00:00Z");
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "alice@example.com", "USER,ADMIN", t);

        User user = mapper.toDomainFromLocalUser(entity);

        assertThat(user.getUserId().value()).isEqualTo("u-1");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getSyncedAt()).isEqualTo(t);
        assertThat(user.getRoles()).containsExactly("USER", "ADMIN");
    }

    @Test
    @DisplayName("INCONSISTENCY-L4 FIXED: toDomainFromLocalUser returns ['USER'] for blank roles, consistent with LocalUserMapper")
    void toDomainFromLocalUserReturnsUserForBlankRoles() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "alice@example.com", "", Instant.now());

        User user = mapper.toDomainFromLocalUser(entity);

        assertThat(user.getRoles()).containsExactly("USER");
    }

    @Test
    @DisplayName("INCONSISTENCY-L4 FIXED: toDomainFromLocalUser returns ['USER'] for null roles, consistent with LocalUserMapper")
    void toDomainFromLocalUserReturnsUserForNullRoles() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "alice@example.com", null, Instant.now());

        User user = mapper.toDomainFromLocalUser(entity);

        assertThat(user.getRoles()).containsExactly("USER");
    }

    @Test
    @DisplayName("BUG-L3b FIXED: toDomainFromLocalUser trims roles")
    void toDomainFromLocalUserTrimsRoles() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "alice@example.com", "USER, ADMIN", Instant.now());

        User user = mapper.toDomainFromLocalUser(entity);

        assertThat(user.getRoles()).containsExactly("USER", "ADMIN");
    }

    @Test
    @DisplayName("toDomainFromLocalUser drops the email (replicated User model has no email field)")
    void toDomainFromLocalUserIsAvailableWithoutEmailConcern() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "alice@example.com", "USER", Instant.now());

        User user = mapper.toDomainFromLocalUser(entity);

        assertThat(user).isNotNull();
    }

    @Test
    @DisplayName("toDomainFromLocalUser returns null for a null entity")
    void toDomainFromLocalUserNullReturnsNull() {
        assertThat(mapper.toDomainFromLocalUser(null)).isNull();
    }
}
