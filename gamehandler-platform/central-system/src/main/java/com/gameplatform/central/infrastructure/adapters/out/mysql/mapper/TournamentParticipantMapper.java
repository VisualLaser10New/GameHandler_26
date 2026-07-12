package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link TournamentParticipant} central domain
 * model and the {@link TournamentParticipantJpaEntity} persistence entity
 * ({@code tournament_participants} table, FASE 4 PIANO &sect;3.3).
 * {@code @Component} instance bean (matches {@code GameDefinitionMapper}); wraps
 * the {@code tournament_id} String primary-key column to/from
 * {@link TournamentId} at the boundary and defends the boxed {@code is_team}
 * column with {@code Boolean.TRUE.equals(...)} (mirroring
 * {@code PlayerStatisticsMapper}).
 */
@Component
public class TournamentParticipantMapper {

    public TournamentParticipant toDomain(TournamentParticipantJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentParticipant(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                Boolean.TRUE.equals(entity.getIsTeam()),
                entity.getDisplayName(),
                entity.getRegisteredAt()
        );
    }

    public TournamentParticipantJpaEntity toEntity(TournamentParticipant domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentParticipantJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.isTeam(),
                domain.getDisplayName(),
                domain.getRegisteredAt()
        );
    }
}