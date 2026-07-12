package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.PlayerMatchFact;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link PlayerMatchFact} central domain model
 * and the {@link PlayerMatchFactJpaEntity} persistence entity. {@code @Component}
 * instance bean (matches {@code GameDefinitionMapper} /
 * {@code LocalAdminBuildingMapper}). Translates the {@code user_id}/
 * {@code building_id} String columns to/from the {@link UserId}/
 * {@link BuildingId} value objects and the {@code game_type}/
 * {@code win_condition} Strings to/from
 * {@link GameType}/{@link WinCondition} enum NAMEs.
 */
@Component
public class PlayerMatchFactMapper {

    public PlayerMatchFact toDomain(PlayerMatchFactJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PlayerMatchFact(
                entity.getSessionId(),
                new UserId(entity.getUserId()),
                new BuildingId(entity.getBuildingId()),
                GameType.valueOf(entity.getGameType()),
                entity.getTournamentId(),
                Boolean.TRUE.equals(entity.getWon()),
                entity.getWinCondition() != null ? WinCondition.valueOf(entity.getWinCondition()) : null,
                entity.getEndedAt()
        );
    }

    public PlayerMatchFactJpaEntity toEntity(PlayerMatchFact domain) {
        if (domain == null) {
            return null;
        }
        return new PlayerMatchFactJpaEntity(
                domain.getSessionId(),
                domain.getUserId().value(),
                domain.getBuildingId().id(),
                domain.getGameType().name(),
                domain.getTournamentId(),
                domain.isWon(),
                domain.getWinCondition() != null ? domain.getWinCondition().name() : null,
                domain.getEndedAt()
        );
    }
}