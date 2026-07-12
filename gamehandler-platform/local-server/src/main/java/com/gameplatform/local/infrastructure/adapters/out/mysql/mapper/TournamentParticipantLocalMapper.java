package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;

/**
 * Null-safe mapper between the {@link TournamentParticipantLocal} domain
 * model and the {@link TournamentParticipantLocalJpaEntity} persistence
 * entity. Mirror of {@link TournamentMatchLocalMapper}.
 */
@Component
public class TournamentParticipantLocalMapper {

    public TournamentParticipantLocal toDomain(TournamentParticipantLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TournamentParticipantLocal(
                new TournamentId(entity.getTournamentId()),
                entity.getParticipantId(),
                entity.getIsTeam() != null && entity.getIsTeam(),
                entity.getDisplayName(),
                entity.getRegisteredAt(),
                entity.getUpdatedAt()
        );
    }

    public TournamentParticipantLocalJpaEntity toEntity(TournamentParticipantLocal domain) {
        if (domain == null) {
            return null;
        }
        return new TournamentParticipantLocalJpaEntity(
                domain.getTournamentId().value(),
                domain.getParticipantId(),
                domain.isTeam(),
                domain.getDisplayName(),
                domain.getRegisteredAt(),
                domain.getUpdatedAt()
        );
    }
}