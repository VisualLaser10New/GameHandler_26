package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link PlayerStatistics} e l'entità persistente
 * {@link PlayerStatisticsJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte la colonna
 * {@code user_id} da/verso {@link UserId} e la colonna {@code game_type}
 * da/verso {@link GameType}.
 *
 * @see PlayerStatistics
 * @see PlayerStatisticsJpaEntity
 */
@Component
public class PlayerStatisticsMapper {

    /**
     * Converte un'entità persistente {@link PlayerStatisticsJpaEntity} nel
     * corrispondente modello di dominio {@link PlayerStatistics}.
     * <p>
     * I campi {@code matchesPlayed} e {@code matchesWon} vengono impostati a
     * {@code 0} se {@code null} nell'entità.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link PlayerStatistics} o {@code null} se l'entità è {@code null}
     * @throws IllegalArgumentException se la stringa {@code game_type} non corrisponde a un valore {@link GameType} valido
     * @see #toEntity(PlayerStatistics)
     */
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

    /**
     * Converte un modello di dominio {@link PlayerStatistics} nell'entità
     * persistente {@link PlayerStatisticsJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link PlayerStatisticsJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(PlayerStatisticsJpaEntity)
     */
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