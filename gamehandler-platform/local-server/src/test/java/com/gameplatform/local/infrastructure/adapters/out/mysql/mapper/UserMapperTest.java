package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.UserJpaEntity;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toEntityAndBackIsSymmetric() {
        Instant synced = Instant.parse("2026-01-01T00:00:00Z");
        User domain = new User(
            new UserId("u-1"), "alice", "hash", List.of("ADMIN", "PLAYER"), synced);

        UserJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getUserId()).isEqualTo("u-1");
        assertThat(entity.getRoles()).isEqualTo("ADMIN,PLAYER");

        User back = mapper.toDomain(entity);
        assertThat(back).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void toDomainNullRolesProducesEmptyList() {
        UserJpaEntity entity = new UserJpaEntity("u", "a", "h", null, Instant.now());
        User back = mapper.toDomain(entity);
        assertThat(back.getRoles()).isEmpty();
    }

    @Test
    void toDomainBlankRolesProducesEmptyList() {
        UserJpaEntity entity = new UserJpaEntity("u", "a", "h", "  ", Instant.now());
        User back = mapper.toDomain(entity);
        assertThat(back.getRoles()).isEmpty();
    }

    @Test
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
