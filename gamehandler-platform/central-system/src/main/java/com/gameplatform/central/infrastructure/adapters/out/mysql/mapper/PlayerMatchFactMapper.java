package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.PlayerMatchFact;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactJpaEntity;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link PlayerMatchFact} e l'entità persistente
 * {@link PlayerMatchFactJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte le colonne
 * {@code user_id} e {@code building_id} da/verso {@link UserId} e
 * {@link BuildingId}, e le colonne {@code game_type} e
 * {@code win_condition} da/verso gli enum {@link GameType} e
 * {@link WinCondition}.
 *
 * @see PlayerMatchFact
 * @see PlayerMatchFactJpaEntity
 */
@Component
public class PlayerMatchFactMapper {

    /**
     * Converte un'entità persistente {@link PlayerMatchFactJpaEntity} nel
     * corrispondente modello di dominio {@link PlayerMatchFact}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link PlayerMatchFact} o {@code null} se l'entità è {@code null}
     * @throws IllegalArgumentException se la stringa {@code game_type} o {@code win_condition} non corrisponde a un valore enum valido
     * @see #toEntity(PlayerMatchFact)
     */
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

    /**
     * Converte un modello di dominio {@link PlayerMatchFact} nell'entità
     * persistente {@link PlayerMatchFactJpaEntity} da persistere.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link PlayerMatchFactJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(PlayerMatchFactJpaEntity)
     */
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