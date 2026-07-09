package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public Reservation toDomain(ReservationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return new Reservation(
            new ReservationId(entity.getId()),
            new GameId(entity.getGameId()),
            new UserId(entity.getUserId()),
            ReservationStatus.valueOf(entity.getStatus()),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getCreatedAt(),
            version
        );
    }

    public ReservationJpaEntity toEntity(Reservation domain) {
        if (domain == null) {
            return null;
        }
        ReservationJpaEntity entity = new ReservationJpaEntity(
            domain.getId().value(),
            domain.getGameId().id(),
            domain.getUserId().value(),
            domain.getStatus().name(),
            domain.getStartTime(),
            domain.getEndTime(),
            domain.getCreatedAt()
        );
        // Always carry the domain version onto the entity so Spring Data uses
        // merge (version != null) instead of persist: merge honours @Version
        // (compares detached.version vs DB.version via SELECT and throws
        // StaleObjectStateException on mismatch) — giving true optimistic
        // locking. New rows carry version=0L which merge INSERTs as initial.
        entity.setVersion(domain.getVersion());
        return entity;
    }
}
