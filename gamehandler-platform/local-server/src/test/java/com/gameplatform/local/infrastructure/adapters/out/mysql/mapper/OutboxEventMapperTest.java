package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventMapperTest {

    private final OutboxEventMapper mapper = new OutboxEventMapper();

    @Test
    void toEntityAndBackIsSymmetric() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        OutboxEvent domain = new OutboxEvent(
            "evt-1", "SESSION_COMPLETED", "{}", "PENDING", created, null, 0);

        OutboxEventJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo("evt-1");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getSentAt()).isNull();
        assertThat(entity.getRetryCount()).isZero();

        OutboxEvent back = mapper.toDomain(entity);
        assertThat(back).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
