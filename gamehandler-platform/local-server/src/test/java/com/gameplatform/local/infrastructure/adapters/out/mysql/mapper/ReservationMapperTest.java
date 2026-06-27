package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationMapperTest {

    private final ReservationMapper mapper = new ReservationMapper();

    @Test
    void toEntityAndBackIsSymmetric() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Instant end = Instant.parse("2026-01-01T11:00:00Z");
        Instant created = Instant.parse("2026-01-01T09:00:00Z");
        Reservation domain = new Reservation(
            new ReservationId("res-1"), new GameId("game-1"), new UserId("user-1"),
            ReservationStatus.CONFIRMED, start, end, created);

        ReservationJpaEntity entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo("res-1");
        assertThat(entity.getGameId()).isEqualTo("game-1");
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        assertThat(entity.getStartTime()).isEqualTo(start);

        Reservation back = mapper.toDomain(entity);

        assertThat(back).usingRecursiveComparison().isEqualTo(domain);
    }

    @Test
    void toDomainNullReturnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toEntityNullReturnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toDomainInvalidStatusThrows() {
        ReservationJpaEntity entity = new ReservationJpaEntity(
            "id", "g", "u", "NOT_A_STATUS",
            Instant.now(), Instant.now().plusSeconds(60), Instant.now());
        assertThatThrownBy(() -> mapper.toDomain(entity))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
