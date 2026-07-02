package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.local.domain.model.LocalSignupUser;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalUserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalUserMapperEdgeCaseTest {

    private final LocalUserMapper mapper = new LocalUserMapper();

    @Test
    @DisplayName("BUG-L3 FIXED: parseRoles trims whitespace, so 'USER, ADMIN' yields ['USER','ADMIN']")
    void parseRolesTrimsWhitespace() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "a@e.com", "USER, ADMIN", Instant.now());

        LocalSignupUser user = mapper.toDomain(entity);

        assertThat(user.getRoles()).containsExactly("USER", "ADMIN");
    }

    @Test
    @DisplayName("parseRoles defaults null roles to ['USER']")
    void parseRolesDefaultsNullToUser() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "a@e.com", null, Instant.now());

        assertThat(mapper.toDomain(entity).getRoles()).containsExactly("USER");
    }

    @Test
    @DisplayName("parseRoles defaults blank roles to ['USER']")
    void parseRolesDefaultsBlankToUser() {
        LocalUserJpaEntity entity = new LocalUserJpaEntity(
                "u-1", "alice", "hash", "a@e.com", "  ", Instant.now());

        assertThat(mapper.toDomain(entity).getRoles()).containsExactly("USER");
    }

    @Test
    @DisplayName("round-trip: empty roles become 'USER' in DB, then ['USER'] when read back (semantic change)")
    void roundTripEmptyRolesBecomesUser() {
        LocalSignupUser domain = new LocalSignupUser(
                new UserId("u-1"), "alice", "hash", "a@e.com", List.of(), Instant.now());

        LocalUserJpaEntity entity = mapper.toEntity(domain);
        LocalSignupUser back = mapper.toDomain(entity);

        assertThat(entity.getRoles()).isEqualTo("USER");
        assertThat(back.getRoles()).containsExactly("USER");
    }

    @Test
    @DisplayName("formatRoles joins multiple roles with a comma (no trimming)")
    void formatRolesJoinsMultipleRoles() {
        LocalSignupUser domain = new LocalSignupUser(
                new UserId("u-1"), "alice", "hash", "a@e.com", List.of("USER", "ADMIN"), Instant.now());

        assertThat(mapper.toEntity(domain).getRoles()).isEqualTo("USER,ADMIN");
    }

    @Test
    @DisplayName("toDomain returns null for a null entity")
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("toEntity returns null for a null domain")
    void toEntityNullReturnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }
}
