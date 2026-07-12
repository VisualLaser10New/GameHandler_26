package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link PlayerStatistics} central domain model
 * and the {@link PlayerStatisticsJpaEntity} persistence entity. {@code @Component}
 * instance bean (matches {@code GameDefinitionMapper}). Translates the
 * {@code user_id}/{@code game_type} String columns to/from
 * {@link UserId}/{@link GameType}.
 */
@Component
public class PlayerStatisticsMapper {

    public PlayerStatistics toDomain(PlayerStatisticsJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PlayerStatistics(
                new UserId(entity.getUserId()),
                GameType.valueOf(entity.getGameType()),
                entity.getMatchesPlayed() != null ? entity.getMatchesPlayed() : 0,
                entity.getMatchesWon() != null ? entity.getMatchesWon() : 0,
                entity.getLastPlayedAt()
        );
    }

    public PlayerStatisticsJpaEntity toEntity(PlayerStatistics domain) {
        if (domain == null) {
            return null;
        }
        return new PlayerStatisticsJpaEntity(
                domain.getUserId().value(),
                domain.getGameType().name(),
                domain.getMatchesPlayed(),
                domain.getMatchesWon(),
                domain.getLastPlayedAt()
        );
    }
}