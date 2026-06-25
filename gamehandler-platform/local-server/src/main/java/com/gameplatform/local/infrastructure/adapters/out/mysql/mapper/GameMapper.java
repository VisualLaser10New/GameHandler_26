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
        return new Game(
            new GameId(entity.getId()),
            GameType.valueOf(entity.getGameType()),
            entity.getName(),
            new BuildingId(entity.getBuildingId()),
            GameMachineStatus.valueOf(entity.getStatus())
        );
    }

    public GameJpaEntity toEntity(Game domain) {
        if (domain == null) {
            return null;
        }
        return new GameJpaEntity(
            domain.getId().id(),
            domain.getGameType().name(),
            domain.getName(),
            domain.getBuildingId().id(),
            domain.getStatus().name()
        );
    }
}
