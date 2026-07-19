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

    /**
     * Converte un'entità JPA {@link TournamentStandingLocalJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentStandingLocal}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
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

    /**
     * Converte un modello di dominio {@link TournamentStandingLocal} nella
     * corrispondente entità JPA {@link TournamentStandingLocalJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
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