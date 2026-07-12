package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link TournamentStandingLocal} domain
 * model and the {@link TournamentStandingLocalJpaEntity} persistence
 * entity. Mirror of {@link TournamentMatchLocalMapper}.
 */
@Component
public class TournamentStandingLocalMapper {

    public TournamentStandingLocal toDomain(TournamentStandingLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentStandingLocal(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                entity.getDisplayName(),
                entity.getWins() != null ? entity.getWins() : 0,
                entity.getLosses() != null ? entity.getLosses() : 0,
                entity.getPoints() != null ? entity.getPoints() : 0,
                entity.getRank(),
                entity.getUpdatedAt()
        );
    }

    public TournamentStandingLocalJpaEntity toEntity(TournamentStandingLocal domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentStandingLocalJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.getDisplayName(),
                domain.getWins(),
                domain.getLosses(),
                domain.getPoints(),
                domain.getRank(),
                domain.getUpdatedAt()
        );
    }
}