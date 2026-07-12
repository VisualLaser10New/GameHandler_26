package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link TournamentStanding} central read-model
 * and the {@link TournamentStandingJpaEntity} persistence entity
 * ({@code tournament_standings} table, FASE 4 PIANO &sect;3.5). {@code @Component}
 * instance bean (matches {@code GameDefinitionMapper}/{@code PlayerStatisticsMapper});
 * wraps the {@code tournament_id} String primary-key column to/from
 * {@link TournamentId} at the boundary, defaults the boxed primitives
 * ({@code wins}/{@code losses}/{@code points}) to {@code 0} on the domain side
 * (mirroring {@code PlayerStatisticsMapper}), and preserves the nullable
 * {@code rank} Integer unchanged.
 */
@Component
public class TournamentStandingMapper {

    public TournamentStanding toDomain(TournamentStandingJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentStanding(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                entity.getWins() != null ? entity.getWins() : 0,
                entity.getLosses() != null ? entity.getLosses() : 0,
                entity.getPoints() != null ? entity.getPoints() : 0,
                entity.getRank()
        );
    }

    public TournamentStandingJpaEntity toEntity(TournamentStanding domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentStandingJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.getWins(),
                domain.getLosses(),
                domain.getPoints(),
                domain.getRank()
        );
    }
}