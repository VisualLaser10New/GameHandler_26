package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentFormat;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link Tournament} central domain model and the
 * {@link TournamentJpaEntity} persistence entity ({@code tournaments} table,
 * FASE 4 PIANO &sect;3.1). {@code @Component} instance bean (matches
 * {@code GameDefinitionMapper}/{@code PlayerStatisticsMapper}); converts the
 * {@code game_type}/{@code format}/{@code status} String columns to/from the
 * {@link GameType}/{@link TournamentFormat}/{@link TournamentStatus} enums via
 * {@code .name()}/{@code valueOf(...)} and wraps the {@code id}/{@code created_by}
 * String columns to/from {@link TournamentId}/{@link UserId} at the boundary.
 * Boxed primitives on the entity ({@code team_based}/{@code team_size}) are
 * null-safe-defaulted on the domain side (mirroring
 * {@code PlayerStatisticsMapper}).
 */
@Component
public class TournamentMapper {

    public Tournament toDomain(TournamentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Tournament(
                new TournamentId(entity.getId()),
                entity.getName(),
                GameType.valueOf(entity.getGameType()),
                Boolean.TRUE.equals(entity.getTeamBased()),
                entity.getTeamSize() != null ? entity.getTeamSize() : 1,
                TournamentFormat.valueOf(entity.getFormat()),
                TournamentStatus.valueOf(entity.getStatus()),
                entity.getStartsAt(),
                entity.getEndsAt(),
                new UserId(entity.getCreatedBy()),
                entity.getCreatedAt()
        );
    }

    public TournamentJpaEntity toEntity(Tournament domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentJpaEntity(
                domain.getTournamentId().value(),
                domain.getName(),
                domain.getGameType().name(),
                domain.isTeamBased(),
                domain.getTeamSize(),
                domain.getFormat().name(),
                domain.getStatus().name(),
                domain.getStartsAt(),
                domain.getEndsAt(),
                domain.getCreatedBy().value(),
                domain.getCreatedAt()
        );
    }
}