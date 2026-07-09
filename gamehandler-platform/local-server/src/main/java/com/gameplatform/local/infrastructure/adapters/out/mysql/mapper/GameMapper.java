package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

@Component
public class GameMapper {

    public Game toDomain(GameJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return new Game(
            new GameId(entity.getId()),
            GameType.valueOf(entity.getGameType()),
            entity.getName(),
            new BuildingId(entity.getBuildingId()),
            entity.getStatus(),
            version
        );
    }

    public GameJpaEntity toEntity(Game domain) {
        if (domain == null) {
            return null;
        }
        GameJpaEntity entity = new GameJpaEntity(
            domain.getId().id(),
            domain.getGameType().name(),
            domain.getName(),
            domain.getBuildingId().id(),
            domain.getStatus()
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
